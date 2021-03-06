package bwfdm.sara.publication;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import bwfdm.sara.publication.db.DAO;
import bwfdm.sara.publication.db.DatabaseField;
import bwfdm.sara.publication.db.PrimaryKey;
import bwfdm.sara.publication.db.TableName;

@TableName("repository")
public class Repository implements DAO {
	@PrimaryKey
	public final UUID uuid;
	@DatabaseField
	public String display_name;
	@DatabaseField
	public String url;
	@DatabaseField
	public String contact_email; // an email address to contact the repository
	@DatabaseField
	public String adapter;
	@DatabaseField
	public String logo_url;
	@DatabaseField
	public String help;
	@DatabaseField
	public boolean enabled;
	@DatabaseField
	public String user_hint;

	public Repository(@JsonProperty("uuid") UUID uuid) {
		this.uuid = uuid;
	}

	public Repository() {
		uuid = null;
	}
}
