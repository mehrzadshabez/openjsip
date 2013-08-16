/*
 *
 *  * This file is part of OpenJSIP project.
 *  *
 *  * OpenJSIP is free software; you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation; either version 2 of the License, or
 *  * (at your option) any later version
 *  * OpenJSIP is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program; if not, write to the Free Software
 *  * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *  *
 *  * Copyright (c) 2013 - Ievgen Krapyva
 *
 */

package openjsip.registrar

import javax.sip.SipFactory
import javax.sip.message.{Response, Request}
import javax.sip.address.{Address, SipURI}
import javax.sip.header._
import openjsip.SipUtils
import org.apache.log4j.Logger
import openjsip.locationservice.LocationService
import java.util.Calendar
import openjsip.authentication.{DigestServerAuthentication, AuthenticationFailedException}

class RegistrarImpl {

  val log = Logger.getLogger("RegistrarImpl")

  val sipFactory = SipFactory.getInstance()
  val headerFactory = sipFactory.createHeaderFactory
  val addressFactory = sipFactory.createAddressFactory
  val messageFactory = sipFactory.createMessageFactory

  val dsam: DigestServerAuthentication = new DigestServerAuthentication("TODO")

  /**
   * The minimum allowed time for binding to expire
   */
  var BINDING_EXPIRE_TIME_MIN: Int = 5
  /**
   * The maximum allowed time for binding to expire
   */
  var BINDING_EXPIRE_TIME_MAX: Int = 3600

  def isDomainServed(s: String): Boolean = ???

  /**
   * This function actually performs registration process.
   * @param request Original REGISTER request
   * @param correctDomain If not null, host part of To header value will be replaced with this <i>correctDomain</i>
   * @return Response to be sent back to client.
   * @throws RequestProcessException exception indicating that a request must be rejected
   */
  @throws[RequestProcessException]
  private def processRegister(request: Request, correctDomain: String): Response = {

    /**
     * First, taking in mind the following:
     *
     * The following header fields, except Contact, MUST be included in a
     * REGISTER request.  A Contact header field MAY be included:
     *
     * Request-URI: The Request-URI names the domain of the location
     * service for which the registration is meant (for example,
     * "sip:chicago.com").  The "userinfo" and "@" components of the
     * SIP URI MUST NOT be present.
     *
     * To: The To header field contains the address of record whose
     * registration is to be created, queried, or modified.  The To
     * header field and the Request-URI field typically differ, as
     * the former contains a user name.  This address-of-record MUST
     * be a SIP URI or SIPS URI.
     *
     * ...we consider that RequestURI and To header must be SIP URIs
     */

    if (!request.getRequestURI.isSipURI)
      throw new RequestProcessException(Response.BAD_REQUEST, "Request-URI is not SIP URI")

    /**
     * 1. The registrar inspects the Request-URI to determine whether it
     * has access to bindings for the domain identified in the
     * Request-URI.  If not, and if the server also acts as a proxy
     * server, the server SHOULD forward the request to the addressed
     * domain, following the general behavior for proxying messages
     * described in Section 16.
     */

    val requestURI = SipUtils.getCanonicalizedURI(request.getRequestURI).asInstanceOf[SipURI]

    if (!isDomainServed(requestURI.getHost))
      throw new RequestProcessException(Response.FORBIDDEN, "The registrar is not responsible for domain " + requestURI.getHost)

    /**
     * 2. To guarantee that the registrar supports any necessary
     * extensions, the registrar MUST process the Require header field
     * values as described for UASs in Section 8.2.2.
     */

    val prh: ProxyRequireHeader = request.getHeader(ProxyRequireHeader.NAME).asInstanceOf[ProxyRequireHeader]

    if (prh != null && !requirementsMet(prh)) {
      // We have to add a Unsupported header listing the Option tags that we don't support:
      val unsupportedHeader: UnsupportedHeader = headerFactory.createUnsupportedHeader(prh.getOptionTag)
      // Let's return a 420 Bad Extension
      throw new RequestProcessException(Response.BAD_EXTENSION, "Extension is not supported", unsupportedHeader)
    }

    // Get location service interface
    val locationService = getLocationService()

    /**
     * 3. A registrar SHOULD authenticate the UAC.  Mechanisms for the
     * authentication of SIP user agents are described in Section 22.
     * Registration behavior in no way overrides the generic
     * authentication framework for SIP.  If no authentication
     * mechanism is available, the registrar MAY take the From address
     * as the asserted identity of the originator of the request.
     */

    authenticateRequest(request, dsam, locationService)

    /**
     * 4. The registrar SHOULD determine if the authenticated user is
     * authorized to modify registrations for this address-of-record.
     * For example, a registrar might consult an authorization
     * database that maps user names to a list of addresses-of-record
     * for which that user has authorization to modify bindings.  If
     * the authenticated user is not authorized to modify bindings,
     * the registrar MUST return a 403 (Forbidden) and skip the
     * remaining steps.
     */

    // Authorized

    /**
     * 5. The registrar extracts the address-of-record from the To header
     * field of the request.  If the address-of-record is not valid
     * for the domain in the Request-URI, the registrar MUST send a
     * 404 (Not Found) response and skip the remaining steps.  The URI
     * MUST then be converted to a canonical form.  To do that, all
     * URI parameters MUST be removed (including the user-param), and
     * any escaped characters MUST be converted to their unescaped
     * form.  The result serves as an index into the list of bindings.
     */
    val toHeader: ToHeader = request.getHeader(ToHeader.NAME).asInstanceOf[ToHeader]

    val address: Address = toHeader.getAddress

    /**
     * The To header field and the Request-URI field typically differ, as
     * the former contains a user name.  This address-of-record MUST
     * be a SIP URI or SIPS URI.
     */
    if (!address.getURI.isSipURI) {
      throw new RequestProcessException(Response.NOT_FOUND, "To header is not SIP URI")
    }

    val toURI = SipUtils.getCanonicalizedURI(toHeader.getAddress.getURI).asInstanceOf[SipURI]

    if (!toURI.getHost.equalsIgnoreCase(requestURI.getHost)) {
      throw new RequestProcessException(Response.NOT_FOUND, "Domain parts in To and RequestURI are not equal")
    }

    // Key is the index to location service database.
    val key = LocationService.getKeyToLocationService(toURI)

    /**
     * 6. The registrar checks whether the request contains the Contact
     * header field.  If not, it skips to the last step.  If the
     * Contact header field is present, the registrar checks if there
     * is one Contact field value that contains the special value "*"
     * and an Expires field.  If the request has additional Contact
     * fields or an expiration time other than zero, the request is
     * invalid, and the server MUST return a 400 (Invalid Request) and
     * skip the remaining steps.  If not, the registrar checks whether
     * the Call-ID agrees with the value stored for each binding.  If
     * not, it MUST remove the binding.  If it does agree, it MUST
     * remove the binding only if the CSeq in the request is higher
     * than the value stored for that binding.  Otherwise, the update
     * MUST be aborted and the request fails.
     */

    val contactHeaders = request.getHeaders(ContactHeader.NAME).asInstanceOf[Iterator[ContactHeader]]

    if (!contactHeaders.isEmpty) {
      // We have one ore more contact headers
      val expiresHeader: ExpiresHeader = request.getHeader(ExpiresHeader.NAME).asInstanceOf[ExpiresHeader]

      val wildcardHeader = contactHeaders.find(p => p.isWildCard)

      // If wildcard contact header was found
      if (wildcardHeader.isDefined) {

        if (contactHeaders.size != 1 || expiresHeader == null || expiresHeader.getExpires != 0) {
          throw new RequestProcessException(Response.BAD_REQUEST, "Expires value is not valid")
        }

        //Everything is ok, we can remove all bindings
        locationService.removeAllBindings(key)
      }
      // Wildcard contact headers were are not found.
      // We process each contact header and add/update the existing bindings.
      else {
        /**
         * 7. The registrar now processes each contact address in the Contact
         * header field in turn.  For each address, it determines the
         * expiration interval as follows:
         *
         * -  If the field value has an "expires" parameter, that value
         * MUST be taken as the requested expiration.
         *
         * -  If there is no such parameter, but the request has an
         * Expires header field, that value MUST be taken as the
         * requested expiration.
         *
         * -  If there is neither, a locally-configured default value MUST
         * be taken as the requested expiration.
         */

        var expiresTime = BINDING_EXPIRE_TIME_MAX

        // Get expiration time form Expires header if exists
        if (expiresHeader != null) {
          expiresTime = expiresHeader.getExpires
        }

        if (expiresTime != 0) {
          // Correct time
          expiresTime = Math.max(expiresTime, BINDING_EXPIRE_TIME_MIN)
          expiresTime = Math.min(expiresTime, BINDING_EXPIRE_TIME_MAX)
        }

        // Move through each contact header
        for (contactHeader <- contactHeaders) {
          var contactExpiresTime = contactHeader.getExpires

          if (contactExpiresTime == -1)
            contactExpiresTime = expiresTime

          if (contactExpiresTime != 0) {
            // Correct time
            contactExpiresTime = Math.max(contactExpiresTime, BINDING_EXPIRE_TIME_MIN)
            contactExpiresTime = Math.min(contactExpiresTime, BINDING_EXPIRE_TIME_MAX)
          }

          // Store expire time as contact parameter
          contactHeader.setExpires(contactExpiresTime)

          // Get existing bindings from location service
          val existingBinding = locationService.getBinding(key, contactHeader)

          val callId = request.getHeader(CallIdHeader.NAME).asInstanceOf[CallIdHeader].getCallId
          val cseq = request.getHeader(CSeqHeader.NAME).asInstanceOf[CSeqHeader].getSeqNumber

          // If existing binding was found
          if (existingBinding != null) {
            /** If the binding does exist, the registrar checks the Call-ID value.
              * If the Call-ID value in the existing binding differs from the
              * Call-ID value in the request, the binding MUST be removed if
              * the expiration time is zero and updated otherwise.  If they are
              * the same, the registrar compares the CSeq value.  If the value
              * is higher than that of the existing binding, it MUST update or
              * remove the binding as above.  If not, the update MUST be
              * aborted and the request fails.
              */

            if (callId.equals(existingBinding.callId) && cseq <= existingBinding.cseq)
              throw new RequestProcessException(Response.BAD_REQUEST, "Call-ID values are not equal or CSeq value is less than existing one")
          }

          //Everything is ok, we can update binding (or remove)

          if (contactHeader.getExpires == 0) {
            locationService.removeBinding(key, contactHeader)
          }
          else {
            locationService.updateRegistration(key, contactHeader, contactHeader.getExpires, callId, cseq)
          }
        }
      }
    }

    // Return the existing bindings
    val existingContactHeaders: List[ContactHeader] = locationService.getContactHeaders(key)

    val response: Response = messageFactory.createResponse(Response.OK, request)

    // Add contacts from bindings to response
    for (contactHeader <- existingContactHeaders)
      response.addHeader(contactHeader)

    /**
     * The response SHOULD include a Date header field.
     */
    val dateHeader: DateHeader = headerFactory.createDateHeader(Calendar.getInstance())
    response.addHeader(dateHeader)

    response
  }

  def authenticateRequest(request: Request, dsam: DigestServerAuthentication, locationService: LocationService) {

    val authorizationHeader: AuthorizationHeader = request.getHeader(AuthorizationHeader.NAME).asInstanceOf[AuthorizationHeader]

    if (authorizationHeader == null)
      throw new RequestProcessException(Response.UNAUTHORIZED, "Authentication header is missing in request")

    val key = LocationService.getKeyToLocationService(request)
    val profile = locationService.getProfile(key)
    val username = profile.getLogin
    val password = if (profile.getPassword != null) profile.getPassword else ""

    var username_h = authorizationHeader.getParameter("username")
    if (username_h == null)
      throw new RequestProcessException(Response.UNAUTHORIZED, "Username parameter is missing in Authorization header")

    if (username_h.indexOf('@') != -1) username_h = username_h.substring(0, username_h.indexOf('@'))

    try {
      dsam.authenticate(request, authorizationHeader, username_h, password)
    } catch {
      case ex: AuthenticationFailedException => {
        val wwwAuthenticateHeader = headerFactory.createWWWAuthenticateHeader("Digest")
        wwwAuthenticateHeader.setParameter("realm", dsam.defaultRealm)
        wwwAuthenticateHeader.setParameter("nonce", dsam.generateNonce(dsam.preferredAlgorithm))
        wwwAuthenticateHeader.setParameter("opaque", "")
        wwwAuthenticateHeader.setParameter("stale", "FALSE")
        wwwAuthenticateHeader.setParameter("algorithm", dsam.preferredAlgorithm)

        throw new RequestProcessException(Response.UNAUTHORIZED, ex.getMessage, wwwAuthenticateHeader)
      }
    }
  }


  private def requirementsMet(prh: ProxyRequireHeader): Boolean = ???

  private def getLocationService(): LocationService = ???
}
