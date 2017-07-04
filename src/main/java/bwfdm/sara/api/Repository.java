package bwfdm.sara.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpSession;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import bwfdm.sara.git.Branch;
import bwfdm.sara.git.Commit;
import bwfdm.sara.git.GitRepo;
import bwfdm.sara.git.GitRepoFactory;
import bwfdm.sara.git.ProjectInfo;
import bwfdm.sara.git.Tag;
import bwfdm.sara.project.Project;
import bwfdm.sara.project.Ref;
import bwfdm.sara.project.Ref.RefType;
import bwfdm.sara.project.RefAction;
import bwfdm.sara.project.RefAction.PublicationMethod;

@RestController
@RequestMapping("/api/repo")
public class Repository {
	@GetMapping("refs")
	public List<RefInfo> getBranches(final HttpSession session) {
		final List<RefInfo> refs = getAllRefs(session);
		Collections.sort(refs);
		loadActions(refs, session);
		return refs;
	}

	@GetMapping("selected-refs")
	public List<Ref> getSelectedBranches(final HttpSession session) {
		final Project project = Project.getInstance(session);
		return new ArrayList<>(project.getRefActions().keySet());
	}

	private List<RefInfo> getAllRefs(final HttpSession session) {
		final GitRepo gl = Project.getInstance(session).getGitRepo();
		final List<RefInfo> refs = new ArrayList<RefInfo>();
		for (final Branch b : gl.getBranches())
			refs.add(new RefInfo(b));
		for (final Tag t : gl.getTags())
			refs.add(new RefInfo(t));
		return refs;
	}

	private void loadActions(final List<RefInfo> refs, final HttpSession session) {
		final Project project = Project.getInstance(session);

		if (project.getRefActions().isEmpty())
			// default to publishing protected branches (assumed to be the main
			// branches), while only archiving the other branches.
			// completely ignore tags by default; they still serve their purpose
			// of marking important points without being archived explicitly.
			for (final RefInfo r : refs)
				if (r.isProtected || r.isDefault)
					project.setRefAction(r.ref, PublicationMethod.PUBLISH_FULL,
							RefAction.HEAD_COMMIT);
				else if (r.ref.type == RefType.BRANCH)
					project.setRefAction(r.ref,
							PublicationMethod.ARCHIVE_PUBLIC,
							RefAction.HEAD_COMMIT);

		final Map<Ref, RefAction> actionMap = project.getRefActions();
		for (final RefInfo r : refs)
			r.action = actionMap.get(r.ref);
	}

	@PostMapping("refs")
	public void setActions(@RequestParam("ref") final String refPath,
			@RequestParam("publish") final PublicationMethod action,
			@RequestParam("firstCommit") final String start,
			final HttpSession session) {
		Project.getInstance(session).setRefAction(Ref.fromPath(refPath),
				action, start);
	}

	@GetMapping("commits")
	public List<? extends Commit> getCommits(
			@RequestParam("ref") final String ref,
			@RequestParam(name = "limit", defaultValue = "20") final int limit,
			final HttpSession session) {
		return GitRepoFactory.getInstance(session).getCommits(ref, limit);
	}

	@GetMapping("project-info")
	public ProjectInfo getProjectInfo(final HttpSession session) {
		return GitRepoFactory.getInstance(session).getProjectInfo();
	}

	@PostMapping("project-info")
	public void setProjectInfo(
			@RequestParam(name = "name", required = false) final String name,
			@RequestParam(name = "description", required = false) final String description,
			final HttpSession session) {
		GitRepoFactory.getInstance(session)
				.updateProjectInfo(name, description);
	}

	@GetMapping("return")
	public RedirectView getReturnURL(final HttpSession session) {
		final GitRepo repo = GitRepoFactory.getInstance(session);
		if (repo.getProjectPath() == null)
			return new RedirectView(repo.getHomePageURL());
		return new RedirectView(repo.getProjectViewURL());
	}

	@GetMapping("edit-file")
	public RedirectView getEditURL(@RequestParam("branch") final String branch,
			@RequestParam("path") final String path, final HttpSession session) {
		final GitRepo repo = GitRepoFactory.getInstance(session);
		if (repo.getBlob("heads/" + branch, path) != null)
			return new RedirectView(repo.getEditURL(branch, path));
		return new RedirectView(repo.getCreateURL(branch, path));
	}
}
