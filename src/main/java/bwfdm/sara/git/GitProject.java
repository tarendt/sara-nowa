package bwfdm.sara.git;

import java.util.List;

import org.eclipse.jgit.api.TransportCommand;

/**
 * This interface exposes all GitLab methods that need to have a project set via
 * {@link #getGitProject(String)}.
 */
public interface GitProject {
	/** @return the url of the "main" page to view a project */
	public String getProjectViewURL();

	// FIXME get & edit used for license only – special versions instead?

	/**
	 * @param branch
	 *            the name of the branch containing the file to edit
	 * @param path
	 *            full, absolute path to file in repo
	 * @return the url of a page where the user can edit the file
	 */
	public String getEditURL(final String branch, String path);

	/**
	 * @param branch
	 *            the name of the branch in which the file is to be created
	 * @param path
	 *            full, absolute path to file in repo
	 * @return the url of a page where the user can create such a file
	 */
	public String getCreateURL(String branch, String path);

	/** @return a list of all branches in the given project */
	public List<Branch> getBranches();

	/** @return a list of all tags in the given project */
	public List<Tag> getTags();

	/** @return the project metadata */
	public ProjectInfo getProjectInfo();

	/**
	 * Enables or disables SARA access to the repo. Can be used to add a system
	 * user to the project. If there is a system-wide user with access to the
	 * repo, this method can be empty.
	 * 
	 * @param enable
	 *            <code>true</code> to enable acces for SARA user,
	 *            <code>false</code> to disable
	 */
	public void enableClone(boolean enable);

	/**
	 * Determine the URL that SARA should use to access the repository. This can
	 * be the same URL that the user would use, but can be a "special" URL as
	 * well, for example to bypass authentication.
	 * 
	 * @return a git repository URI, in any syntax that JGit understands (but
	 *         preferably SSH-based)
	 */
	public String getCloneURI();

	/**
	 * Sets the credentials for authenticating access to the repository. Can be
	 * username/password or SSH keys (or anything else supported by JGit /
	 * JSch).
	 * <p>
	 * Must be bracketed in calls to {@link #enableClone(boolean)} because the
	 * credentials might only be created by {@code enableClone(true)}.
	 * 
	 * @param tx
	 *            a {@link TransportCommand} that will be used to access
	 *            {@link #getCloneURI()} and that should have its credentials
	 *            set
	 */
	public void setCredentials(TransportCommand<?, ?> tx);

	/**
	 * @param ref
	 *            git ref, should be {@code heads/master} or {@code tags/test}
	 * @param limit
	 *            maximum number of items to return. GitLab clamps this to 100
	 *            max
	 * @return a list of the first few commits in a given branch or tag
	 */
	public List<Commit> getCommits(final String ref, final int limit);

	/**
	 * @param ref
	 *            branch or tag containing the file, in git ref format (ie.
	 *            {@code heads/master} or {@code tags/test})
	 * @param path
	 *            full path to a file in the repo, without the initial slash
	 * @return the contents of the file as a byte array, or <code>null</code> if
	 *         the file doesn't exist
	 */
	public byte[] getBlob(String ref, String path);
}
