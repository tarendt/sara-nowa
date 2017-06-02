package bwfdm.sara.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpSession;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import bwfdm.sara.git.Branch;
import bwfdm.sara.git.Commit;
import bwfdm.sara.git.GitRepo;
import bwfdm.sara.git.GitRepoFactory;
import bwfdm.sara.git.Tag;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@RestController
@RequestMapping("/api/repo")
public class Repo {
	@GetMapping("refs")
	public List<Ref> getBranches(final HttpSession session) {
		final List<Ref> refs = getAllRefs(GitRepoFactory.getInstance(session));
		Collections.sort(refs);
		loadActions(refs, session);
		return refs;
	}

	private List<Ref> getAllRefs(final GitRepo gl) {
		final List<Ref> refs = new ArrayList<Ref>();
		for (final Branch b : gl.getBranches())
			refs.add(new Ref(b));
		for (final Tag t : gl.getTags())
			refs.add(new Ref(t));
		return refs;
	}

	private void loadActions(final List<Ref> refs, final HttpSession session) {
		// FIXME load from database instead
		if (session.getAttribute("branch_actions") == null) {
			final HashMap<String, Action> map = new HashMap<String, Action>();
			// default to publishing protected branches (assumed to be the main
			// branches), while only archiving everything else.
			for (final Ref r : refs) {
				if (r.isProtected || r.isDefault)
					r.action = Action.PUBLISH_FULL;
				map.put(r.ref, r.action);
			}
			session.setAttribute("branch_actions", map);
		}

		@SuppressWarnings("unchecked")
		final Map<String, Action> actions = (Map<String, Action>) session
				.getAttribute("branch_actions");
		for (final Ref r : refs)
			r.action = actions.get(r.ref);

		@SuppressWarnings("unchecked")
		final Map<String, String> starts = (Map<String, String>) session
				.getAttribute("branch_starts");
		if (starts != null)
			for (final Ref r : refs)
				r.start = starts.get(r.ref);
	}

	@PostMapping("refs")
	public void setActions(@RequestParam("ref") final String ref,
			@RequestParam("action") final Action action,
			@RequestParam("start") final String start, final HttpSession session) {
		// FIXME store in database instead
		if (session.getAttribute("branch_actions") == null)
			session.setAttribute("branch_actions",
					new HashMap<String, Action>());
		@SuppressWarnings("unchecked")
		final Map<String, Action> actions = (Map<String, Action>) session
				.getAttribute("branch_actions");
		actions.put(ref, action);

		if (session.getAttribute("branch_starts") == null)
			session.setAttribute("branch_starts", new HashMap<String, String>());
		@SuppressWarnings("unchecked")
		final Map<String, String> starts = (Map<String, String>) session
				.getAttribute("branch_starts");
		starts.put(ref, start);
	}

	/** data class for refs in the branch selection screen. */
	@JsonInclude(Include.NON_NULL)
	private static class Ref implements Comparable<Ref> {
		/**
		 * user-friendly name of ref, ie. {@code master} or {@code foo}. doesn't
		 * identify whether it's a branch or tag.
		 */
		@JsonProperty("name")
		private final String name;
		/** ref in git syntax, ie. {@code heads/master} or {@code tags/foo} */
		@JsonProperty("ref")
		private final String ref;
		/** ref type (branch or tag). */
		@JsonProperty("type")
		private final RefType type;
		/** <code>true</code> if this is a protected branch */
		@JsonProperty("protected")
		private final boolean isProtected;
		/** <code>true</code> if this is the default branch */
		@JsonProperty("default")
		private final boolean isDefault;
		/** selected archival option. */
		@JsonProperty("action")
		private Action action;
		/** ID of first commit to archive. */
		@JsonProperty("start")
		public String start;

		private Ref(final Branch b) {
			type = RefType.BRANCH;
			ref = "heads/" + b.getName();
			name = b.getName();
			isProtected = b.isProtected();
			isDefault = b.isDefault();
		}

		private Ref(final Tag t) {
			type = RefType.TAG;
			ref = "tags/" + t.getName();
			name = t.getName();
			isProtected = t.isProtected();
			isDefault = false;
		}

		@Override
		public int compareTo(final Ref other) {
			// put default branch first so that it's the one selected by
			// default
			if (isDefault)
				return -1;
			if (other.isDefault)
				return +1;
			// branches before tags
			if (type == RefType.BRANCH && other.type != RefType.BRANCH)
				return -1;
			if (other.type == RefType.BRANCH && type != RefType.BRANCH)
				return +1;
			// protected branches are more likely to be the "main" branches,
			// so put them before unprotected (likely "side") branches
			if (isProtected && !other.isProtected)
				return -1;
			if (other.isProtected && !isProtected)
				return +1;
			// tiebreaker within those groups: lexicographic ordering
			return name.compareTo(other.name);
		}

		@Override
		public String toString() {
			return "Ref{" + ref + ", " + (isProtected ? "protected, " : "")
					+ "action=" + action + "}";
		}
	}

	private enum Action {
		PUBLISH_FULL, PUBLISH_ABBREV, PUBLISH_LATEST, ARCHIVE_PUBLIC, ARCHIVE_HIDDEN
	}

	private enum RefType {
		BRANCH, TAG
	}

	@GetMapping("commits")
	public List<? extends Commit> getCommits(
			@RequestParam("ref") final String ref,
			@RequestParam(name = "limit", defaultValue = "20") final int limit,
			final HttpSession session) {
		return GitRepoFactory.getInstance(session).getCommits(ref, limit);
	}
}