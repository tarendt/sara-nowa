package bwfdm.sara.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpSession;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import bwfdm.sara.gitlab.Branch;
import bwfdm.sara.gitlab.GitLab;
import bwfdm.sara.gitlab.ProjectInfo;
import bwfdm.sara.gitlab.Tag;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@RestController
@RequestMapping("/api")
public class REST {
	@GetMapping("refs")
	public List<Ref> getBranches(@RequestParam("project") final String project,
			final HttpSession session) {
		final String token = (String) session.getAttribute("gitlab_token");
		final GitLab gl = new GitLab(Temp.GITLAB, project, token);
		final List<Ref> refs = getAllRefs(gl);
		final ProjectInfo projectInfo = gl.getProjectInfo();
		sortRefs(refs, projectInfo.master);
		loadActions(refs, session);
		return refs;
	}

	private void sortRefs(final List<Ref> refs, final String master) {
		refs.sort(new Comparator<Ref>() {
			@Override
			public int compare(final Ref a, final Ref b) {
				// put default branch first so that it's the one selected by
				// default
				if (a.name.equals(master))
					return -1;
				if (b.name.equals(master))
					return +1;
				// branches before tags
				if (a.type.equals("branch") && !b.type.equals("branch"))
					return -1;
				if (b.type.equals("branch") && !a.type.equals("branch"))
					return +1;
				// protected branches are more likely to be the "main" branches,
				// so put them before unprotected (likely "side") branches
				if (a.prot && !b.prot)
					return -1;
				if (b.prot && !a.prot)
					return +1;
				// tiebreaker within those groups: lexicographic ordering
				return a.name.compareTo(b.name);
			}
		});
	}

	private List<Ref> getAllRefs(final GitLab gl) {
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
				if (r.prot)
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
	}

	@PostMapping("refs")
	public void setActions(@RequestParam("project") final String project,
			@RequestParam("ref") final String ref,
			@RequestParam("action") final Action action,
			final HttpSession session) {
		// FIXME store in database instead
		if (session.getAttribute("branch_actions") == null)
			session.setAttribute("branch_actions",
					new HashMap<String, Action>());
		@SuppressWarnings("unchecked")
		final Map<String, Action> actions = (Map<String, Action>) session
				.getAttribute("branch_actions");
		actions.put(ref, action);
	}

	@JsonInclude(Include.NON_NULL)
	private static class Ref {
		@JsonProperty("name")
		private final String name;
		@JsonProperty("ref")
		private final String ref;
		@JsonProperty("type")
		private final String type;
		@JsonProperty("protected")
		private final boolean prot;
		@JsonProperty("action")
		private Action action;

		private Ref(final Branch b) {
			type = "branch";
			ref = "heads/" + b.name;
			name = b.name;
			prot = b.prot;
		}

		private Ref(final Tag t) {
			type = "tag";
			ref = "tags/" + t.name;
			name = t.name;
			// branches CAN be protected, but the API doesn't return that field
			prot = false;
		}

		@Override
		public String toString() {
			return "Ref{" + ref + ", " + (prot ? "protected, " : "")
					+ "action=" + action + "}";
		}
	}

	private enum Action {
		PUBLISH_FULL, PUBLISH_ABBREV, PUBLISH_LATEST, ARCHIVE_PUBLIC, ARCHIVE_HIDDEN
	}
}
