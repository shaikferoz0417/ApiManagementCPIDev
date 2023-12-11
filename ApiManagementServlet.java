package com.aramco.btp.digitalsupplier;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Base64;

import javax.annotation.Resource;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.sap.cloud.account.TenantContext;
import com.sap.core.connectivity.api.configuration.ConnectivityConfiguration;
import com.sap.core.connectivity.api.configuration.DestinationConfiguration;
import com.sap.security.um.service.UserManagementAccessor;
import com.sap.security.um.user.User;
import com.sap.security.um.user.UserProvider;

/**
 * Servlet class making http calls to specified http destinations. Destinations
 * are used in the following example connectivity scenarios:<br>
 * - Connecting to an outbound Internet resource using HTTP destinations<br>
 * - Connecting to an on-premise backend using on premise HTTP destinations,<br>
 * where the destinations have no authentication.<br>
 */
@WebServlet("/*")
public class ApiManagementServlet extends HttpServlet {
	@Resource
	private TenantContext tenantContext;

	private static final long serialVersionUID = 1L;
	private static final int COPY_CONTENT_BUFFER_SIZE = 1024;

	private static final String ON_PREMISE_PROXY = "OnPremise";

	public ApiManagementServlet() {
	}

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String requestURL = request.getRequestURI();
		String queryParams = request.getQueryString();
		String servletPath = request.getServletPath();
		// Get the ServletContext
		ServletContext servletContext = getServletContext();

		servletContext.getServletContextName();

		// Retrieve the application name
		String appName = servletContext.getServletContextName();

		System.out.println("appName----------->" + appName);

		HttpURLConnection urlConnection = null;
		HttpURLConnection urlConnectionVendor = null;

		String destinationName = request.getParameter("destname");
		String destinationNameHanaDB = request.getParameter("destname");

		// The default request to the Servlet will use
		// outbound-internet-destination--HanaDB
		if (destinationNameHanaDB == null) {
			destinationNameHanaDB = "db-access-destination";
		}

		// The default request to the Servlet will use outbound-internet-destination
		if (destinationName == null) {
			destinationName = "cpi-access-destination";
		}

		try {
			// Look up the connectivity configuration API
			Context ctx = new InitialContext();
			ConnectivityConfiguration configuration = (ConnectivityConfiguration) ctx
					.lookup("java:comp/env/connectivityConfiguration");

			// / UserProvider provides access to the user storage
			UserProvider users = UserManagementAccessor.getUserProvider();

			// Read the currently logged in user from the user storage
			User user = users.getUser(request.getUserPrincipal().getName());

			// Get destination configuration for "destinationName"
			DestinationConfiguration destConfiguration = configuration.getConfiguration(destinationName);

			// Get destination configuration for "destinationName" --HanaDB
			DestinationConfiguration destConfigurationHanaDB = configuration.getConfiguration(destinationNameHanaDB);

			if (destConfiguration == null || destConfigurationHanaDB == null) {

				String errorMessage = "{\"message\": \"Destination " + destinationName + " is not found. Hint:"
						+ "Make sure to have the destination configured.\" }";
				response.setStatus(400);
				response.setContentType("application/json");
				response.setCharacterEncoding("UTF-8");
				response.getWriter().write(errorMessage);
				response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
						String.format(
								"Destination %s is not found. Hint:" + " Make sure to have the destination configured.",
								destinationName));
				return;
			}

			// Get the destination URL
			String value = destConfiguration.getProperty("URL") + servletPath;

			String[] result = null;
			if (requestURL != null) {
				if (requestURL != null || requestURL != "") {
					result = requestURL.split("apimanagementcpi-application");
				}
				if (result != null && result.length > 0) {
					value = value + result[1];
				}
			}
			System.out.println("requestURL@108------------>" + requestURL);
			System.out.println("requestURL@109------------>" + queryParams);

			// TO GET USER VENDOR

			String authTypeHanaDB = destConfigurationHanaDB.getProperty("Authentication");
			String proxyTypeHanaDB = destConfigurationHanaDB.getProperty("ProxyType");
			Proxy proxyHanaDB = getProxy(proxyTypeHanaDB);
			String userpassHanaDB = destConfigurationHanaDB.getProperty("User") + ":"
					+ destConfigurationHanaDB.getProperty("Password");
			String basicAuthHanaDB = "Basic " + new String(Base64.getEncoder().encode(userpassHanaDB.getBytes()));

			// In prod comment this
			String vendorPath = destConfigurationHanaDB.getProperty("URL") + "/COMMON/vendorid/" + "AN01403921429-T";
			/*
			 * In prod uncomment this
			 * String vendorPath = destConfigurationHanaDB.getProperty("URL") +
			 * "/COMMON/vendorid/"
			 * + user.getAttribute("ANID");
			 */

			URL urlVendor = new URL(vendorPath);
			urlConnectionVendor = (HttpURLConnection) urlVendor.openConnection(proxyHanaDB);
			injectHeader(urlConnectionVendor, proxyTypeHanaDB);

			if (authTypeHanaDB.equals("BasicAuthentication")) {
				urlConnectionVendor.setRequestProperty("Authorization", basicAuthHanaDB);
			}

			BufferedReader brVendor = new BufferedReader(new InputStreamReader((urlConnectionVendor.getInputStream())));
			StringBuilder sbVendor = new StringBuilder();
			String outputVendor;

			while ((outputVendor = brVendor.readLine()) != null) {
				sbVendor.append(outputVendor);
			}
			String responseMessageVendor = sbVendor.toString();

			System.out.println("Vendor--------->" + responseMessageVendor);

			// String decodedValue;
			// String vendorFilter;
			String updatedQuery = "";
			String updatedQueryEncoded = queryParams;
			boolean isQueryUpdated = false;
			String updatedQueryWithLifnr = "";

			if (responseMessageVendor != null & responseMessageVendor.trim() != ""
					&& responseMessageVendor.trim() != "|") {

				// Added for Forecast Collaboration
				if (requestURL.contains("/http/SNC/SS/Visibility/GET/VendorAgreementSet")
						|| requestURL.contains("/http/SNC/SS/Visibility/GET/AgreementSet")
						|| requestURL.contains("/http/SNC/SSV/ETA/Daily")
						|| requestURL.contains("/http/SCM/DS/GetHistoryDetailSet")
						|| requestURL.contains("/http/SNC/SSV/Mat/Values")
						|| requestURL.contains("/http/SCM/DS/GetMaterialDetailSet")

				) {
					isQueryUpdated = true;
					// check for $
					if (queryParams != null && queryParams.contains("$filter")) {
						updatedQueryWithLifnr = insertStringAtIndex(updatedQueryEncoded,
								"Lifnr%20eq%20%27" + "10005434" + "%27%20and%20", 8);
					} else {
						updatedQueryWithLifnr += "$filter=Lifnr%20eq%20%27" + "10005434" + "%27";
					}

				}

				// Added for RFQ APIs
				if (requestURL.contains("/http/SNC/DetailHeader") ||
						requestURL.contains("http/SNC/RFQ/GET/MatAttachment")) {
					isQueryUpdated = true;
					// check for $
					if (queryParams != null && queryParams.contains("$filter")) {
						updatedQueryWithLifnr = insertStringAtIndex(updatedQueryEncoded,
								"Lifnr%20eq%20%27" + "10000030" + "%27%20and%20", 8);
					} else {
						updatedQueryWithLifnr += "$filter=Lifnr%20eq%20%27" + "10000030" + "%27";
					}

				}

				if (requestURL.contains("http/SNC/RFQ/GET/MatAttachment/Stream")) {
					isQueryUpdated = false;
					// Do nothing
				}

				if (requestURL.contains("/http/SNC/SuppPerformanceReport")
						|| requestURL.contains("/http/SNC/SPR/Download/SupplierProfileReport")) {
					isQueryUpdated = true;
					// check for $
					if (queryParams != null && queryParams.contains("$filter")) {
						updatedQueryWithLifnr = insertStringAtIndex(updatedQueryEncoded,
								"Vendor%20eq%20%27" + "10005434" + "%27", 8);
						// updatedQueryEncoded.replace("VendorNumber", responseMessageVendor);
					} else {
						updatedQueryWithLifnr += "Vendor=" + "'10005434'";
					}
				}

				if (requestURL.contains("/http/SNC/SS/Visibility/GET/CountriesSet?$select=CountryCode,CountryName")) {
					// Do nothing
				}

				System.out.println("updatedQueryWithLifnr--------->206" + updatedQueryWithLifnr);

				updatedQueryEncoded = encodeValue(updatedQuery);
				if (!isQueryUpdated && queryParams != null) {
					value = value + "?" + queryParams;
				} else {
					// value = value + "?" + updatedQuery;
					// value = value + "?" + updatedQueryEncoded;
					value = value + "?" + updatedQueryWithLifnr;
				}

				String authType = destConfiguration.getProperty("Authentication");
				String proxyType = destConfiguration.getProperty("ProxyType");
				String userpass = destConfiguration.getProperty("User") + ":" +
						destConfiguration.getProperty("Password");
				String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userpass.getBytes()));
				Proxy proxy = getProxy(proxyType);
				URL url = new URL(value);
				urlConnection = (HttpURLConnection) url.openConnection(proxy);

				if (authType.equals("BasicAuthentication")) {
					urlConnection.setRequestProperty("Authorization", basicAuth);
				}

				injectHeader(urlConnection, proxyType);

				// Copy content from the incoming response to the outgoing response

				InputStream instream = urlConnection.getInputStream();
				OutputStream outstream = response.getOutputStream();
				copyStream(instream, outstream);

			}

			/*
			 * else {
			 * 
			 * // value = value + "/" + "blank";
			 * }
			 */
		} catch (Exception e) {

			e.printStackTrace();

			BufferedReader br = new BufferedReader(new InputStreamReader((urlConnection.getErrorStream())));
			StringBuilder sb = new StringBuilder();
			String output;

			while ((output = br.readLine()) != null) {
				sb.append(output);
			}
			// Connectivity operation failed
			String errorMessage = sb.toString();
			response.setStatus(urlConnection.getResponseCode());
			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");
			response.getWriter().write(errorMessage);
		}
	}

	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String requestURL = request.getRequestURI();
		String queryParams = request.getQueryString();
		String servletPath = request.getServletPath();
		HttpURLConnection urlConnection = null;

		String destinationName = request.getParameter("destname");

		// The default request to the Servlet will use outbound-internet-destination
		if (destinationName == null) {
			destinationName = "cpi-access-destination";
		}

		String payloadRequest = getBody(request);

		//////////////////////////////////////////////
		try {
			// Look up the connectivity configuration API
			Context ctxRole = new InitialContext();
			ConnectivityConfiguration configurationRole = (ConnectivityConfiguration) ctxRole
					.lookup("java:comp/env/connectivityConfiguration");

			// / UserProvider provides access to the user storage
			UserProvider usersRole = UserManagementAccessor.getUserProvider();
			// Read the currently logged in user from the user storage
			User userRole = usersRole.getUser(request.getUserPrincipal().getName());
			// Get destination configuration for "destinationName"
			DestinationConfiguration destConfiguration = configurationRole.getConfiguration(destinationName);
			if (destConfiguration == null) {

				String errorMessage = "{\"message\": \"Destination " + destinationName + " is not found. Hint:"
						+ "Make sure to have the destination configured.\" }";
				response.setStatus(400);
				response.setContentType("application/json");
				response.setCharacterEncoding("UTF-8");
				response.getWriter().write(errorMessage);
				response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
						String.format(
								"Destination %s is not found. Hint:" + " Make sure to have the destination configured.",
								destinationName));
				return;
			}

			String value = destConfiguration.getProperty("URL") + servletPath;
			if (queryParams != null) {
				value = value + "?" + queryParams;
			}

			String[] result = null;
			if (requestURL != null || requestURL != "") {
				result = requestURL.split("apimanagementcpi-application");
			}

			if (result != null && result.length > 0) {
				value = value + result[1];
			}

			URL url = new URL(value);
			String authType = destConfiguration.getProperty("Authentication");
			String proxyType = destConfiguration.getProperty("ProxyType");
			Proxy proxy = getProxy(proxyType);
			String userpass = destConfiguration.getProperty("User") + ":"
					+ destConfiguration.getProperty("Password");
			String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userpass.getBytes()));
			urlConnection = (HttpURLConnection) url.openConnection(proxy);
			urlConnection.setDoOutput(true);
			urlConnection.setDoInput(true);

			// Set headers
			urlConnection.setRequestProperty("Content-Type", "application/json");
			// urlConnection.setRequestProperty("Accept", "application/json");
			urlConnection.setRequestMethod("POST");

			if (authType.equals("BasicAuthentication")) {

				urlConnection.setRequestProperty("Authorization", basicAuth);
			}
			try (OutputStream os = urlConnection.getOutputStream()) {
				byte[] input = payloadRequest.getBytes("utf-8");
				os.write(input, 0, input.length);
			}

			injectHeader(urlConnection, proxyType);

			// Copy content from the incoming response to the outgoing response

			InputStream instream = urlConnection.getInputStream();
			OutputStream outstream = response.getOutputStream();
			copyStream(instream, outstream);

		} catch (Exception e) {
			e.printStackTrace(); // Log the exception
			BufferedReader br = new BufferedReader(new InputStreamReader((urlConnection.getErrorStream())));
			StringBuilder sb = new StringBuilder();
			String output;

			while ((output = br.readLine()) != null) {
				sb.append(output);
			}
			// Connectivity operation failed
			String errorMessage = sb.toString();
			response.setStatus(urlConnection.getResponseCode());
			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");
			response.getWriter().write(errorMessage);

		}

	}

	private Proxy getProxy(String proxyType) {
		Proxy proxy = Proxy.NO_PROXY;
		String proxyHost = null;
		String proxyPort = null;

		if (ON_PREMISE_PROXY.equals(proxyType)) {
			// Get proxy for on-premise destinations
			proxyHost = System.getenv("HC_OP_HTTP_PROXY_HOST");
			proxyPort = System.getenv("HC_OP_HTTP_PROXY_PORT");
		} else {
			// Get proxy for internet destinations
			proxyHost = System.getProperty("https.proxyHost");
			proxyPort = System.getProperty("https.proxyPort");
		}

		if (proxyPort != null && proxyHost != null) {
			int proxyPortNumber = Integer.parseInt(proxyPort);
			proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPortNumber));
		}

		return proxy;
	}

	private void injectHeader(HttpURLConnection urlConnection, String proxyType) {
		if (ON_PREMISE_PROXY.equals(proxyType)) {
			// Insert header for on-premise connectivity with the consumer account name
			urlConnection.setRequestProperty("SAP-Connectivity-ConsumerAccount",
					tenantContext.getTenant().getAccount().getId());

		}
	}

	private void copyStream(InputStream inStream, OutputStream outStream) throws IOException {
		byte[] buffer = new byte[COPY_CONTENT_BUFFER_SIZE];
		int len;
		while ((len = inStream.read(buffer)) != -1) {

			outStream.write(buffer, 0, len);
		}
	}

	public static String getBody(HttpServletRequest request) throws IOException {

		String body = null;
		StringBuilder stringBuilder = new StringBuilder();
		BufferedReader bufferedReader = null;

		try {
			InputStream inputStream = request.getInputStream();
			if (inputStream != null) {
				bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
				char[] charBuffer = new char[128];
				int bytesRead = -1;
				while ((bytesRead = bufferedReader.read(charBuffer)) > 0) {
					stringBuilder.append(charBuffer, 0, bytesRead);
				}
			} else {
				stringBuilder.append("");
			}
		} catch (IOException ex) {
			throw ex;
		} finally {
			if (bufferedReader != null) {
				try {
					bufferedReader.close();
				} catch (IOException ex) {
					throw ex;
				}
			}
		}

		body = stringBuilder.toString();

		return body;
	}

	// Decodes a URL encoded string using `UTF-8`
	public static String decodeValue(String value) {
		try {
			return URLDecoder.decode(value, StandardCharsets.UTF_8.toString());
		} catch (UnsupportedEncodingException ex) {
			throw new RuntimeException(ex.getCause());
		}
	}

	// Decodes a URL encode string using `UTF-8`
	public static String encodeValue(String value) {
		try {
			return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
		} catch (UnsupportedEncodingException ex) {
			throw new RuntimeException(ex.getCause());
		}
	}

	public static String addFilterCondition(String originalQuery, String key, String newCondition) {
		String filterKeyword = "$filter=";
		String updatedQuery = originalQuery;

		// Split the original query into individual parameters
		String[] queryParams = originalQuery.split("&");

		boolean filterFound = false;

		// Loop through each parameter to find and update the $filter parameter
		for (int i = 0; i < queryParams.length; i++) {
			if (queryParams[i].startsWith(filterKeyword)) {
				filterFound = true;
				String filterPart = queryParams[i].substring(filterKeyword.length());

				// Check if the new condition is empty or null
				if (newCondition.isEmpty()) {
					// Handle case where the new condition is empty or null
					return originalQuery;
				}

				// Pattern to match the Lifnr condition
				String lifnrPattern = "(" + key + "\\s+\\w{2}\\s+'[^']*')"; // Pattern considering the operator
																			// length as 2
																			// characters

				if (filterPart.contains(key + " eq ''")) {
					// If Lifnr condition is empty, replace it with the new condition
					queryParams[i] = queryParams[i].replaceAll("Lifnr\\s+eq\\s+''", newCondition);
				} else if (filterPart.contains(key)) {
					// If Lifnr condition exists, replace it with the new condition
					queryParams[i] = queryParams[i].replaceAll(lifnrPattern, newCondition);
				} else if (filterPart.contains(")")) {
					// If other conditions exist and there are closing brackets, add the new Lifnr
					// condition with 'and'
					queryParams[i] = queryParams[i].replace(")", " and " + newCondition + ")");
				} else {
					// If no Lifnr condition or brackets, directly add the new Lifnr condition
					queryParams[i] = queryParams[i].replace(filterPart,
							"(" + filterPart + " and " + newCondition + ")");
				}
			}
		}

		// Reconstruct the updated query from the modified parameters
		updatedQuery = String.join("&", queryParams);

		// If the $filter parameter wasn't found, add it with the new condition
		if (!filterFound && !newCondition.isEmpty()) {
			updatedQuery += "&" + filterKeyword + newCondition;
		}

		return updatedQuery;
	}

	public static String insertStringAtIndex(String originalString, String stringToInsert, int index) {
		StringBuilder stringBuilder = new StringBuilder(originalString);
		stringBuilder.insert(index, stringToInsert);
		return stringBuilder.toString();
	}

}