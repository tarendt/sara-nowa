package bwfdm.sara.git.gitlab;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/** low-level helper class for making REST calls to the GitLab API. */
class RESTHelper {
	/**
	 * date format pattern used by GitLab, {@link SimpleDateFormat} style.
	 * currently ISO8601 ({@code 2012-09-20T11:50:22.000+03:00}).
	 */
	static final String DATE_FORMAT_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
	/**
	 * date format used by GitLab, as a {@link SimpleDateFormat}. currently
	 * ISO8601 ({@code 2012-09-20T11:50:22.000+03:00}).
	 */
	static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(
			DATE_FORMAT_PATTERN);

	private static final int MAX_PER_PAGE = 100;
	private static final Pattern LINK_URL_FIELD = Pattern.compile("<(.*)>");
	private static final Pattern LINK_REL_NEXT_FIELD = Pattern
			.compile("rel=(\"?)next\\1");

	private final String root;
	private final RestTemplate rest;
	private HttpEntity<Void> auth;
	private MultiValueMap<String, String> authMap;

	/**
	 * @param root
	 *            URL to GitLab root
	 * @param project
	 *            name of project whose API to access
	 */
	RESTHelper(final String root, final String project) {
		this.root = root + "/api/v4/projects/"
				+ UrlEncode.encodePathSegment(project);
		rest = new RestTemplate();
	}

	void setToken(final String token) {
		if (token == null) {
			auth = null;
			return;
		}

		authMap = new LinkedMultiValueMap<String, String>();
		authMap.set("Authorization", "Bearer " + token);
		auth = new HttpEntity<Void>(authMap);
	}

	UriComponentsBuilder uri(final String endpoint) {
		return UriComponentsBuilder.fromHttpUrl(root + endpoint);
	}

	/**
	 * Convenience function for calling
	 * {@link #get(UriComponentsBuilder, ParameterizedTypeReference)}.
	 */
	<T> T get(final String endpoint, final ParameterizedTypeReference<T> type) {
		return get(uri(endpoint), type);
	}

	/**
	 * Get a single item from GitLab. Works for lists, but will only return the
	 * first page of 100 elements at most. Use
	 * {@link #getList(String, ParameterizedTypeReference)} to get all elements.
	 * 
	 * @param endpoint
	 *            API path, relative to project
	 * @param type
	 *            instance of {@link ParameterizedTypeReference} with correct
	 *            type parameters
	 * @return a list of all objects that GitLab returns
	 */
	<T> T get(final UriComponentsBuilder ucb,
			final ParameterizedTypeReference<T> type) {
		return rest.exchange(ucb.build(true).toUri(), HttpMethod.GET, auth,
				type).getBody();
	}

	byte[] getBlob(final UriComponentsBuilder ucb) {
		return rest.exchange(ucb.build(true).toUri(), HttpMethod.GET, auth,
				byte[].class).getBody();
	}

	/**
	 * Get a list of items from GitLab, working around the pagination misfeature
	 * by requesting pages one by one. If you need just a few, use
	 * {@link #get(UriComponentsBuilder, ParameterizedTypeReference)} and set
	 * {@code per_page} manually.
	 * 
	 * @param endpoint
	 *            API path, relative to project
	 * @param type
	 *            instance of {@link ParameterizedTypeReference} with correct
	 *            type parameters
	 * @return a list of all objects that GitLab returns
	 */
	<T> List<T> getList(final String endpoint,
			final ParameterizedTypeReference<List<T>> type) {
		return getList(uri(endpoint), type);
	}

	<T> List<T> getList(final UriComponentsBuilder ucb,
			final ParameterizedTypeReference<List<T>> type) {
		final List<T> list = new ArrayList<T>();
		// 100 per page is the limit. use it for max efficiency.
		URI uri = ucb.queryParam("per_page", Integer.toString(MAX_PER_PAGE))
				.build(true).toUri();
		do {
			final ResponseEntity<List<T>> resp = rest.exchange(uri,
					HttpMethod.GET, auth, type);
			list.addAll(resp.getBody());
			// work around the pagination misfeature
			uri = getNextLink(resp, uri);
		} while (uri != null);
		return list;
	}

	/** Workaround for GitLab's pagination "feature". */
	private URI getNextLink(final ResponseEntity<?> resp, final URI base) {
		final List<String> linkHeaders = resp.getHeaders()
				.get(HttpHeaders.LINK);
		if (linkHeaders == null)
			return null; // header missing; probably no next page

		for (final String linkHeader : linkHeaders)
			for (final String link : linkHeader.split(",\\s*")) {
				final String[] fields = link.split(";\\s*");
				final Matcher m = LINK_URL_FIELD.matcher(fields[0]);
				if (!m.matches())
					throw new IllegalArgumentException(
							"malformed Link header: " + link);
				final String url = m.group(1);

				for (int i = 1; i < fields.length; i++) {
					if (LINK_REL_NEXT_FIELD.matcher(fields[i]).matches())
						try {
							// to handle relative URLs
							return new URL(base.toURL(), url).toURI();
						} catch (MalformedURLException | URISyntaxException e) {
							throw new IllegalArgumentException(
									"malformed URL in Link header: " + url, e);
						}
				}
			}
		return null; // header present but no "next" link, ie. last page
	}

	/**
	 * Convenience function for calling
	 * {@link #put(UriComponentsBuilder, Object)}.
	 */
	void put(final String endpoint, final Object args) {
		put(uri(endpoint), args);
	}

	void put(final UriComponentsBuilder ucb, final Object args) {
		final HttpEntity<Object> data = new HttpEntity<>(args, authMap);
		rest.exchange(ucb.build(true).toUri(), HttpMethod.PUT, data,
				(Class<?>) null);
	}

	/**
	 * Convenience function for calling
	 * {@link #post(UriComponentsBuilder, Object)}.
	 */
	void post(final String endpoint, final Object args) {
		post(uri(endpoint), args);
	}

	void post(final UriComponentsBuilder ucb, final Object args) {
		final HttpEntity<Object> data = new HttpEntity<>(args, authMap);
		rest.exchange(ucb.build(true).toUri(), HttpMethod.POST, data,
				(Class<?>) null);
	}
}
