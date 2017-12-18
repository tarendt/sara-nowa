package bwfdm.sara.db;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import bwfdm.sara.git.ArchiveRepo;
import bwfdm.sara.git.ArchiveRepoFactory;
import bwfdm.sara.git.GitRepo;
import bwfdm.sara.git.GitRepoFactory;

/**
 * Database containing config stuff for the web frontend. Effectively read-only.
 * <p>
 * Implementation should work with PostgreSQL, MySQL (untested) and HSQL
 * (embedded database used for development) because it only uses trivial (CRUD,
 * but without U) queries.
 */
public class ConfigDatabase {
	public static final String LICENSES_TABLE = "public.fe_supported_licenses";
	private static final String GITREPOS_TABLE = "public.source";
	private static final String GITREPO_PARAM_TABLE = "public.source_params";
	private static final String ARCHIVES_TABLE = "public.archive";
	private static final String ARCHIVE_PARAM_TABLE = "public.archive_params";

	private final JacksonTemplate db;

	/**
	 * Creates a DAO for reading config values.
	 *
	 * @param db
	 *            the {@link DataSource} to use for all queries
	 */
	public ConfigDatabase(final DataSource db) {
		this.db = new JacksonTemplate(db);
	}

	/**
	 * @return a list of all licenses defined in the config, in order of
	 *         preference
	 */
	public List<License> getLicenses() {
		return db.queryRowToList("select id, display_name, info_url from "
				+ LICENSES_TABLE + " where not hidden"
				+ " order by preference asc, id asc", License.class);
	}

	public String getLicenseText(final String id) {
		return db.querySingleToObject(
				"select full_text from " + LICENSES_TABLE + " where id = ?",
				String.class, id);
	}

	/** @return a list of all supported git repos */
	public List<GitRepoFactory> getGitRepos() {
		return db.<GitRepoFactory> queryRowToList(
				"select uuid, display_name, adapter from " + GITREPOS_TABLE,
				GitRepoFactory.class);
	}

	/**
	 * Constructs a new {@link GitRepo} object, using the parameters stored in
	 * the database for the given id.
	 *
	 * @param id
	 *            git repo name used in the {@value GITLABS_TABLE} table
	 * @return a new instance of the named {@link GitRepo}
	 */
	public GitRepo newGitRepo(final String id) {
		final GitRepoFactory factory = db.queryRowToObject(
				"select uuid, display_name, logo_base64, adapter from "
						+ GITREPOS_TABLE + " where uuid = UUID(?)",
				GitRepoFactory.class, id);
		return factory.newGitRepo(readArguments(GITREPO_PARAM_TABLE, id));
	}

	/**
	 * @return the ID of the (single) GitArchive
	 * @deprecated temporary hack until we have IR selection
	 */
	@Deprecated
	public String getGitArchive() {
		return db.querySingleToObject("select uuid from " + ARCHIVES_TABLE,
				String.class);
	}

	/**
	 * Constructs a new {@link ArchiveRepo} object, using the parameters stored
	 * in the database for the given id.
	 *
	 * @param id
	 *            git repo name used in the {@value #GITREPOS_TABLE} table
	 * @return a new instance of the named {@link ArchiveRepo}
	 */
	public ArchiveRepo newGitArchive(final String id) {
		final ArchiveRepoFactory factory = db.queryRowToObject(
				"select uuid, display_name, logo_base64, adapter from "
						+ ARCHIVES_TABLE + " where uuid = UUID(?)",
				ArchiveRepoFactory.class, id);
		return factory.newArchiveRepo(readArguments(ARCHIVE_PARAM_TABLE, id));
	}

	private Map<String, String> readArguments(final String table,
			final String id) {
		return db.querySingleToMap(
				"select param, value from " + table + " where id = UUID(?)",
				"param", String.class, "value", String.class, id);
	}
}
