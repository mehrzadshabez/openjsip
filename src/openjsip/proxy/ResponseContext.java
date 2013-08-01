/**
 * This file is part of OpenJSIP, a free SIP service components.
 *
 * OpenJSIP is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version
 *
 * OpenJSIP is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * Copyright (c) 2009 - Yevgen Krapiva
 */
package openjsip.proxy;

import gov.nist.javax.sip.message.SIPResponse;

import javax.sip.message.Response;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.header.ProxyAuthenticateHeader;
import javax.sip.header.Header;
import javax.sip.header.WWWAuthenticateHeader;
import java.util.Vector;
import java.util.ListIterator;
import java.text.ParseException;

public class ResponseContext
{
    /**
     * Vector to store final responses
     */
    private final Vector<Response> finalResponses;

    /**
     * The proxy SHOULD give preference to responses that provide information affecting
     * resubmission of request, such as 401, 407, 415, 420, and 484
     */
    private static final int[] preferencedCodes = { 401, 407, 415, 420, 484 };

    /**
     * Original request. Is used to construct a Response object
     */
    private Request request;

    /**
     * Constructs response context
     * @param request Associated request
     */
    public ResponseContext(Request request)
    {
        this.request = request;
        finalResponses = new Vector<Response>();
    }

    /**
     * Adds final response to this response context. If response is not final, it will not be added
     * @param response Response
     */
    public void addFinalResponse(Response response)
    {
        if (((SIPResponse) response).isFinalResponse())
            finalResponses.addElement(response);
    }

    /**
     * Chooses among all <i>responses</i> those that have specified <i>statusClass</i> (i.e. 1 for 1XX, 2 for 2XX etc.)
     * @param responses Responses vector
     * @param statusClass Status code class
     * @return Vector with responses that have specified <i>statusClass</i>
     */
    public static Vector getResponsesByStatusCodeClass(Vector responses, int statusClass)
    {
        Vector<Response> v = null;

        for (int i = 0; i < responses.size(); i++)
        {
            Response response = (Response) responses.elementAt(i);
            if (response.getStatusCode() / 100 == statusClass)
            {
                if (v == null) v = new Vector<Response>();
                v.add(response);
            }
        }

        return v;
    }

    /**
     * Chooses among all <i>responses</i> those that have specified <i>statusCode</i>
     * @param responses Responses vector
     * @param statusCode Status code
     * @return Vector with responses that have specified <i>statusCode</i>
     */
    public static Vector getResponsesByStatusCode(Vector responses, int statusCode)
    {
        Vector<Response> v = null;

        for (int i = 0; i < responses.size(); i++)
        {
            Response response = (Response) responses.elementAt(i);
            if (response.getStatusCode() == statusCode)
            {
                if (v == null) v = new Vector<Response>();
                v.add(response);
            }
        }

        return v;
    }

    /**
     * Chooses the best responses from vector of final responses. Also performs auth headers aggregation as specified in section 16.7 step 7.
     * @param messageFactory MessageFactory object for constructing responses
     * @return The best response
     * @throws ParseException
     */
    public Response getBestResponse(MessageFactory messageFactory) throws ParseException
    {
        /**
         * If there are no final responses in the context, the proxy MUST
         * send a 408 (Request Timeout) response to the server
         * transaction.
         */
        if (finalResponses.isEmpty())
            return messageFactory.createResponse(Response.REQUEST_TIMEOUT, request);

        /**
         * Otherwise, the proxy MUST forward a response from the responses
         * stored in the response context.  It MUST choose from the 6xx
         * class responses if any exist in the context.
         */
        Vector _6xxResponses = getResponsesByStatusCodeClass(finalResponses, 6);
        if (_6xxResponses != null && !_6xxResponses.isEmpty())
            return messageFactory.createResponse(((Response) _6xxResponses.firstElement()).getStatusCode(), request);

        /**
         * If no 6xx class
         * responses are present, the proxy SHOULD choose from the lowest
         * response class stored in the response context.  The proxy MAY
         * select any response within that chosen class.
         */

        Vector _2xxResponses = getResponsesByStatusCodeClass(finalResponses, 2);
        if (_2xxResponses != null && !_2xxResponses.isEmpty())
            return messageFactory.createResponse(((Response) _2xxResponses.firstElement()).getStatusCode(), request);

        Vector _3xxResponses = getResponsesByStatusCodeClass(finalResponses, 3);
        if (_3xxResponses != null && !_3xxResponses.isEmpty())
            return messageFactory.createResponse(((Response) _3xxResponses.firstElement()).getStatusCode(), request);

        /**
         * The proxy SHOULD
         * give preference to responses that provide information affecting
         * resubmission of this request, such as 401, 407, 415, 420, and
         * 484 if the 4xx class is chosen.
         */
        Vector _4xxResponses = getResponsesByStatusCodeClass(finalResponses, 4);
        if (_4xxResponses != null && !_4xxResponses.isEmpty())
        {
            for (int preferencedCode : preferencedCodes)
            {
                Vector responses = getResponsesByStatusCode(_4xxResponses, preferencedCode);
                if (responses != null && !responses.isEmpty())
                    return aggregateAuthHeaders(messageFactory.createResponse(((Response) responses.firstElement()).getStatusCode(), request));
            }

            return aggregateAuthHeaders(messageFactory.createResponse(((Response) _4xxResponses.firstElement()).getStatusCode(), request));
        }

        /**
         * A proxy which receives a 503 (Service Unavailable) response
         * SHOULD NOT forward it upstream unless it can determine that any
         * subsequent requests it might proxy will also generate a 503.
         * In other words, forwarding a 503 means that the proxy knows it
         * cannot service any requests, not just the one for the Request-
         * URI in the request which generated the 503.  If the only
         * response that was received is a 503, the proxy SHOULD generate
         * a 500 response and forward that upstream.
         */
        Vector _5xxResponses = getResponsesByStatusCodeClass(finalResponses, 5);
        if (_5xxResponses != null && !_5xxResponses.isEmpty())
        {
            Vector _503Responses = getResponsesByStatusCode(_5xxResponses, 503);
            if (_503Responses != null && !_503Responses.isEmpty() && _503Responses.size() == _5xxResponses.size())
                return messageFactory.createResponse(500, request);

            return messageFactory.createResponse(((Response) _5xxResponses.firstElement()).getStatusCode(), request);
        }

        return null;
    }

    /**
     * performs auth headers aggregation as specified in section 16.7 step 7.
     * @param bestResponse Best response
     * @return The same response with aggregated auth headers
     */
    private Response aggregateAuthHeaders(Response bestResponse)
    {
        /**
         * 7.  Aggregate Authorization Header Field Values
         *
         * If the selected response is a 401 (Unauthorized) or 407 (Proxy
         * Authentication Required), the proxy MUST collect any WWW-
         * Authenticate and Proxy-Authenticate header field values from
         * all other 401 (Unauthorized) and 407 (Proxy Authentication
         * Required) responses received so far in this response context
         * and add them to this response without modification before
         * forwarding.  The resulting 401 (Unauthorized) or 407 (Proxy
         * Authentication Required) response could have several WWW-
         * Authenticate AND Proxy-Authenticate header field values.
         *
         * This is necessary because any or all of the destinations the
         * request was forwarded to may have requested credentials.  The
         * client needs to receive all of those challenges and supply
         * credentials for each of them when it retries the request.
         * Motivation for this behavior is provided in Section 26.
         */

        int bestResponseStatusCode = bestResponse.getStatusCode();
        switch(bestResponseStatusCode)
        {
            case Response.UNAUTHORIZED:
            case Response.PROXY_AUTHENTICATION_REQUIRED:

                for (int i = 0; i < finalResponses.size(); i++)
                {
                    Response finalResponse = finalResponses.elementAt(i);
                    if (finalResponse.equals(bestResponse) || finalResponse.getStatusCode() != bestResponseStatusCode) continue;

                    ListIterator authHeaders = finalResponse.getHeaders(bestResponseStatusCode == Response.UNAUTHORIZED ? WWWAuthenticateHeader.NAME : ProxyAuthenticateHeader.NAME);
                    while (authHeaders.hasNext())
                        bestResponse.addHeader((Header) authHeaders.next());
                }

                break;
        }

        return bestResponse;
    }

}
