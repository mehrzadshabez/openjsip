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
package openjsip;

import gov.nist.javax.sip.address.SipUri;

import javax.sip.message.Request;
import javax.sip.message.MessageFactory;
import javax.sip.message.Response;
import javax.sip.header.ToHeader;
import javax.sip.SipProvider;
import javax.sip.ServerTransaction;
import javax.sip.InvalidArgumentException;
import javax.sip.SipException;
import javax.sip.address.URI;
import javax.sip.address.Address;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;


public class SipUtils
{
    public static final String OPENJSIP_VERSION = "0.0.4";
    public static final String BRANCH_MAGIC_COOKIE = "z9hG4bK";
    
    /**
	 * toHex
	 */
    private static final char[] toHex = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

    private SipUtils()
    {

    }

    /**
     * @return Branch id value
     */
    public static String generateBranchId()
    {
        StringBuffer ret = new StringBuffer();
        StringBuffer b = new StringBuffer();
        String hex;

        b.append(Integer.toString((int) (Math.random() * 10000)));
        b.append(System.currentTimeMillis());

        try
        {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            byte[] bytes = messageDigest.digest(b.toString().getBytes());
            hex = toHexString(bytes);
        }
        catch(NoSuchAlgorithmException ex)
        {
            hex = "NoSuchAlgorithmExceptionMD5";
        }

        ret.append(BRANCH_MAGIC_COOKIE + hex);


        return ret.toString();
    }

    /**
     * @param b Array of bytes
     * @return A hexadecimal string
     */
    public static String toHexString(byte[] b)
    {
        int pos = 0;
        char[] c = new char[b.length * 2];

        for (int i = 0; i < b.length; i++)
        {
            c[pos++] = toHex[(b[i] >> 4) & 0x0F];
            c[pos++] = toHex[b[i] & 0x0f];
        }

        return new String(c);
    }   

    /**
     * If URI is the SIP URI, method returns its clone without any parameters,
     * otherwise original URI is returned without any modifications.
     * @param uri URI
     * @return Canonicalized URI ( without parameters )
     */
    public static URI getCanonicalizedURI(URI uri)
    {
        if (uri != null && uri.isSipURI())
        {
            SipUri sipUri = (SipUri) uri.clone();
            sipUri.clearUriParms();

            return sipUri;
        }
        else
            return uri;
    }

    /**
     * @param request Original request
     * @return The key value to use with location service database. Key is needed to find current location of subscriber specified in To header of <i>request</i>. Returns null if To header is not SIP URI like.
     */
    public static String getKeyToLocationService(Request request)
    {
        ToHeader toHeader = (ToHeader) request.getHeader(ToHeader.NAME);
        if (toHeader != null)
        {
            Address address = toHeader.getAddress();

            /**
             * The To header field and the Request-URI field typically differ, as
             * the former contains a user name.  This address-of-record MUST
             * be a SIP URI or SIPS URI.
             */
            return getKeyToLocationService(address.getURI());
        }

        return null;
    }

    /**
     * @param uri URI to make key value from.
     * @return The key value to use with location service database. Key is needed to find current location of subscriber by <i>uri</i>. Returns null if <i>uri</i> is not SIP URI like.
     */
    public static String getKeyToLocationService(URI uri)
    {
        URI canonicalizedUri = SipUtils.getCanonicalizedURI(uri);
        if (canonicalizedUri.isSipURI())
        {
            // Clearing just parameters as specified in RFC is not enough
            SipUri sipUri = (SipUri) canonicalizedUri;
            sipUri.clearPassword();
            sipUri.removePort();
            sipUri.clearQheaders();

            return sipUri.toString();
        }


        return null;
    }

    /**
     * Creates and dispatches response
     * @param responseId Response identifier
     * @param sipProvider SipProvider object
     * @param messageFactory MessageFactory object
     * @param request Request object
     * @param serverTransaction ServerTransaction object
     * @throws java.text.ParseException
     * @throws javax.sip.InvalidArgumentException
     * @throws javax.sip.SipException
     */
    public static void sendResponse(int responseId, SipProvider sipProvider, MessageFactory messageFactory, Request request, ServerTransaction serverTransaction) throws ParseException, InvalidArgumentException, SipException
    {
        Response response = messageFactory.createResponse(responseId, request);
        if (serverTransaction != null)
            serverTransaction.sendResponse(response);
        else
            sipProvider.sendResponse(response);
    }

    public static void sendResponseSafe(int errorId, SipProvider sipProvider, MessageFactory messageFactory, Request request, ServerTransaction serverTransaction)
    {
        try
        {
            Response response = messageFactory.createResponse(errorId, request);
            if (serverTransaction != null)
                serverTransaction.sendResponse(response);
            else
                sipProvider.sendResponse(response);
        }
        catch (Exception ex)
        {

        }       
    }
        
}
