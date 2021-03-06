package bwfdm.sara.publication.dspace;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.MultiValueMap;
import org.swordapp.client.AuthCredentials;
import org.swordapp.client.Deposit;
import org.swordapp.client.DepositReceipt;
import org.swordapp.client.EntryPart;
import org.swordapp.client.ProtocolViolationException;
import org.swordapp.client.SWORDClient;
import org.swordapp.client.SWORDClientException;
import org.swordapp.client.SWORDCollection;
import org.swordapp.client.SWORDError;
import org.swordapp.client.SWORDWorkspace;
import org.swordapp.client.ServiceDocument;
import org.swordapp.client.UriRegistry;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import bwfdm.sara.publication.Hierarchy;
import bwfdm.sara.publication.PublicationRepository;
import bwfdm.sara.publication.Repository;
import bwfdm.sara.publication.SaraMetaDataField;

public class DSpace_SwordOnly implements PublicationRepository {

	class SDData {
		public SDData(ServiceDocument sd, List<SWORDWorkspace> ws) {
			this.sd = sd;
			this.ws = ws;
		}

		public final ServiceDocument sd;
		public final List<SWORDWorkspace> ws;
	}

	protected static final Logger logger = LoggerFactory
			.getLogger(DSpace_SwordOnly.class);

	private final String swordUser, swordPwd, swordApiEndpoint,
			swordServiceDocumentRoot;
	private final Repository dao;

	// for SWORD
	private SWORDClient swordClient;

	// for IR
	private final String depositType;
	private final boolean checkLicense;
	private final String publicationType;
	private final boolean showUnsubmittable;

	private final String name_mapping;
	private final Integer limit_upload_size;

	private Map<String, Hierarchy> hierarchyMap;

	@JsonCreator
	public DSpace_SwordOnly(@JsonProperty("sword_user") final String su,
			@JsonProperty("sword_pwd") final String sp,
			@JsonProperty("sword_api_endpoint") final String se,
			@JsonProperty("deposit_type") final String dt,
			@JsonProperty("check_license") final boolean cl,
			@JsonProperty("show_unsubmittable") final boolean shu,
			@JsonProperty(value = "publication_type", required = false) final String pt,
			@JsonProperty(value = "name_mapping", required = false) final String nm,
			@JsonProperty(value = "limit_upload_size", required = false) final String lus,
			@JsonProperty("dao") final Repository dao) {
		this.dao = dao;

		swordUser = su;
		swordPwd = sp;
		swordApiEndpoint = se;
		swordServiceDocumentRoot = swordApiEndpoint + "/servicedocument";

		depositType = dt;
		checkLicense = cl;
		publicationType = pt;
		showUnsubmittable = shu;

		if (nm != null) {
			name_mapping = nm;
		} else {
			name_mapping = "$2, $1";
		}

		Integer i;
		try {
			i = Integer.parseInt(lus);
		} catch (Exception e) {
			i = 0;
			logger.info(e.getMessage());
		}
		limit_upload_size = i;

		swordClient = new SWORDClient();

		hierarchyMap = new HashMap<>();
	}

	public SDData serviceDocument(final AuthCredentials authCredentials,
			final String sdURL_) {
		ServiceDocument sd = null;
		List<SWORDWorkspace> ws = null;
		final String sdURL = (sdURL_ == null) ? swordServiceDocumentRoot
				: sdURL_;

		try {
			sd = swordClient.getServiceDocument(sdURL, authCredentials);
			if (sd != null)
				ws = sd.getWorkspaces();
		} catch (SWORDClientException | ProtocolViolationException e) {
			logger.error("Exception by accessing service document: "
					+ e.getClass().getSimpleName() + ": " + e.getMessage());
			return null;
		}

		return new SDData(sd, ws);
	}

	@Override
	public boolean isAccessible() {
		// checks whether the root service document is accessible
		return (serviceDocument(new AuthCredentials(swordUser, swordPwd),
				null) != null);
	}

	@Override
	public boolean isUserRegistered(final String loginName) {
		// checks whether the user has access / is registered
		return (serviceDocument(
				new AuthCredentials(swordUser, swordPwd, loginName),
				null).sd != null);
	}

	@Override
	public boolean isUserAssigned(final String loginName) {
		final SDData sdd = serviceDocument(
				new AuthCredentials(swordUser, swordPwd, loginName), null);

		if (sdd.sd == null)
			return false;

		Hierarchy hierarchy = getHierarchy(loginName);
		return (hierarchy.getCollectionCount() > 0);
	}

	private Hierarchy buildHierarchyLevel(final Hierarchy hierarchy,
			final String sdURL, final String loginName) {
		final SDData sdd = serviceDocument(
				new AuthCredentials(swordUser, swordPwd, loginName), sdURL);
		String lvlName = null;
		SWORDWorkspace root = null;

		if (sdd.ws != null) {
			if (sdd.ws.size() != 1) {
				logger.error(
						"Something is strange! There should be exactly one top-level workspace!");
				return null;
			} else {
				root = sdd.ws.get(0);
				lvlName = root.getTitle();
				logger.info("Found bibliography level " + lvlName);
			}
		}

		hierarchy.setName(lvlName);

		for (final SWORDCollection coll : root.getCollections()) {
			final List<String> subservices = coll.getSubServices();
			boolean isCollection = subservices.isEmpty();

			Hierarchy child = new Hierarchy(coll.getTitle(), null);
			child.setURL(coll.getHref().toString());

			final String[] chops = child.getURL().split("/");
			try {
				child.setHandle(chops[chops.length - 2] + "/"
						+ chops[chops.length - 1]);
			} catch (ArrayIndexOutOfBoundsException e) {
				logger.error(
						"Cannot obtain a valid DSpace handle from the URL!");
				child.setHandle(null);
			}

			if (isCollection) {
				logger.info("FOUND COLLECTION " + child.getName());
				child.setCollection(true);
				if (checkLicense) {
					try {
						child.setPolicy(coll.getCollectionPolicy());
					} catch (ProtocolViolationException e) {
						logger.info("No policy found for " + coll.getTitle()
								+ "! Collections must deliver a policy!");
					}
				}
				hierarchy.addChild(child);
			} else {
				logger.info("FOUND COMMUNITY " + child.getName());
				child.setCollection(false);
				for (String sd : subservices) {
					hierarchy.addChild(
							buildHierarchyLevel(child, sd, loginName));
				}
			}
		}

		return hierarchy;
	}

	@Override
	public Hierarchy getHierarchy(String loginName) {
		if (!hierarchyMap.containsKey(loginName)) {
			Hierarchy h = new Hierarchy(null, null);
			h = buildHierarchyLevel(h, swordServiceDocumentRoot, loginName);
			if (!showUnsubmittable) {
				h.pruneUnsubmittable();
			}
			hierarchyMap.put(loginName, h);
		}
		return hierarchyMap.get(loginName);
	}

	@Override
	public SubmissionInfo publish(final String userLogin,
			final String collectionURL, final File fileFullPath,
			MultiValueMap<String, String> metadataMap) {

		String mimeFormat = "application/atom+xml";
		String packageFormat = UriRegistry.PACKAGE_BINARY;

		// TODO do some zip file upload handling magic...
		if (limit_upload_size == 0) {
			// TODO don't upload no file
			logger.info("ZIP file deposit disabled");
		} else {
			// TODO
			// 1)check whether ZIP file has a size<=limit
			// if yes: upload file
			// if no: don't upload no file
			// logger.info("File size limit okay - ZIP file will be
			// deposited!");
			logger.info(
					"File size limit exceeded - ZIP file will not be deposited!");
		}

		return publishElement(userLogin, collectionURL, mimeFormat,
				packageFormat, fileFullPath, metadataMap);
	}

	private SubmissionInfo publishElement(String userLogin,
			String collectionURL, String mimeFormat, String packageFormat,
			File file, MultiValueMap<String, String> metadataMap) {

		// FIXME TODO
		SubmissionInfo submissionInfo = new SubmissionInfo();

		// Check if only 1 parameter is used (metadata OR file).
		// Multipart is not supported.
		if (((file != null) && (metadataMap != null))
				|| ((file == null) && (metadataMap == null))) {
			return null;
		}

		AuthCredentials authCredentials = new AuthCredentials(swordUser,
				swordPwd, userLogin);

		Deposit deposit = new Deposit();

		try {
			// Check if "meta data as a Map"
			if (metadataMap != null) {
				EntryPart ep = new EntryPart();
				for (final MultiValueMap.Entry<String, List<String>> metadataEntry : metadataMap
						.entrySet()) {
					// FIXME this is a bit hacky
					if (metadataEntry.getKey()
							.equals(SaraMetaDataField.TYPE.getDisplayName())) {
						if (publicationType != null) {
							metadataEntry
									.setValue(Arrays.asList(publicationType));
						}
					}
					// write ordered list of meta data
					for (final String m : metadataEntry.getValue()) {
						ep.addDublinCore(metadataEntry.getKey(), m);
					}
				}
				deposit.setEntryPart(ep);
			}

			// Check if "file"
			if (file != null) {
				deposit.setFile(new FileInputStream(file));
				// deposit requires a "filename" parameter
				// --> in curl: -H "Content-Disposition: filename=file.zip"
				deposit.setFilename(file.getName());
			}

			deposit.setMimeType(mimeFormat);
			deposit.setPackaging(packageFormat);

			if (depositType != null) {
				switch (depositType.toLowerCase()) {
				case "workflow":
					deposit.setInProgress(false);
					break;
				default: // "workspace"
					deposit.setInProgress(true);
					break;
				}
			} else {
				deposit.setInProgress(true);
			}
			submissionInfo.inProgress = deposit.isInProgress();

			DepositReceipt receipt = swordClient.deposit(collectionURL, deposit,
					authCredentials);

			String[] parts = receipt.getLocation().split("/");

			if (receipt.getSplashPageLink() != null) {
				submissionInfo.edit_ref = receipt.getSplashPageLink().getHref();
			}
			submissionInfo.item_ref = parts[parts.length - 1];
			return submissionInfo;

		} catch (FileNotFoundException e) {
			logger.error("Exception by accessing a file: "
					+ e.getClass().getSimpleName() + ": " + e.getMessage());
			return null;

		} catch (SWORDClientException | SWORDError
				| ProtocolViolationException e) {
			logger.error("Exception by making deposit: "
					+ e.getClass().getSimpleName() + ": " + e.getMessage());
			return null;
		}
	}

	@Override
	public Repository getDAO() {
		return dao;
	}

	@Override
	public void dump() {

	}

	@Override
	public String mappedName(String givenName, String surName) {
		final String nameRegex = "(\\S{2,})\\s{1,}(.*)";
		return new String(givenName + " " + surName).replaceAll(nameRegex,
				name_mapping);
	}
}
