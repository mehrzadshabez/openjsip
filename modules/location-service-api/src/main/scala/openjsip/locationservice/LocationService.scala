/*
 * This file is part of OpenJSIP project.
 *
 * OpenJSIP is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version
 * OpenJSIP is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * Copyright (c) 2013 - Ievgen Krapyva
 */

package openjsip.locationservice

import gov.nist.javax.sip.address.SipUri
import javax.sip.header.{ToHeader, ContactHeader}
import javax.sip.address.{Address, URI}
import javax.sip.message.Request
import openjsip.SipUtils

trait LocationService {

  /**
   * @param key Key to location service directory
   * @return Returns the username of specified subscriber
   * @throws UserNotFoundException If the subscriber specified by <i>key</i> cannot be found
   */
  @throws(classOf[UserNotFoundException])
  def getUsername(key: String): String

  /**
   * @param key Key to location service directory
   * @return Returns the password of specified subscriber, or empty string if unspecified
   * @throws UserNotFoundException If the subscriber specified by <i>key</i> cannot be found
   */
  @throws(classOf[UserNotFoundException])
  def getPassword(key: String): String

  /**
   * Create or update binding for subscriber.
   * @param key Key to location service directory
   * @param contactHeader Contact address that is associated with the subscriber <i>key</i> for the duration of <i>expires</i>
   * @param expires Time-to-live value for this binding measured in seconds.
   * @param callId CallID parameter from REGISTER request
   * @param cseq CSeQ parameter from REGISTER request
   * @throws UserNotFoundException If the subscriber specified by <i>key</i> cannot be found
   */
  @throws(classOf[UserNotFoundException])
  def updateRegistration(key: String, contactHeader: ContactHeader, expires: Long, callId: String, cseq: Long): Unit

  /**
   * Removes one contact address binding of subscriber.
   * @param key Key to location service directory
   * @param contactHeader Contact address that is associated with the subscriber <i>key</i>.
   * @throws UserNotFoundException If the subscriber specified by <i>key</i> cannot be found
   */
  @throws(classOf[UserNotFoundException])
  def removeBinding(key: String, contactHeader: ContactHeader)

  /**
   * Removes all contact address bindings of subscriber.
   * @param key Key to location service directory
   * @throws UserNotFoundException If the subscriber specified by <i>key</i> cannot be found
   */
  @throws(classOf[UserNotFoundException])
  def removeAllBindings(key: String)

  /**
   * @param key Key to location service directory
   * @return Returns all contact addresses associated with the subscriber.
   * @throws UserNotFoundException If the subscriber specified by <i>key</i> cannot be found
   */
  @throws(classOf[UserNotFoundException])
  def getContactHeaders(key: String): List[ContactHeader]

  /**
   * @param key Key to location service directory
   * @param contactHeader Contact address that is associated with the subscriber <i>key</i>.
   * @return Returns the binding of subscriber whose contact address matches <i>contactHeader</i>, null otherwise.
   * @throws UserNotFoundException If the subscriber specified by <i>key</i> cannot be found
   */
  @throws(classOf[UserNotFoundException])
  def getBinding(key: String, contactHeader: ContactHeader): Binding


  /**
   * @return The list of domains location service has provisioned subscribers
   */
  def getDomains: Set[String]

  /**
   * @return Default domain
   */
  def getDefaultDomain: String

  /**
   * @param uri URI to make key value from.
   * @return The key value to use with location service database. Key is needed to find current location of subscriber by <i>uri</i>. Returns null if <i>uri</i> is not SIP URI like.
   */
  def getKeyToLocationService(uri: URI): String = {

    val canonicalizedUri: URI = SipUtils.getCanonicalizedURI(uri)

    if (canonicalizedUri.isSipURI) {
      val sipUri: SipUri = canonicalizedUri.asInstanceOf[SipUri]
      sipUri.clearPassword()
      sipUri.removePort()
      sipUri.clearQheaders()
      sipUri.toString
    } else {
      null
    }
  }

  /**
   * @param request Original request
   * @return The key value to use with location service database. Key is needed to find current location of subscriber specified in To header of <i>request</i>. Returns null if To header is not SIP URI like.
   */
  def getKeyToLocationService(request: Request): String = {
    val toHeader: ToHeader = request.getHeader(ToHeader.NAME).asInstanceOf[ToHeader]
    if (toHeader != null) {
      val address: Address = toHeader.getAddress

      /**
       * The To header field and the Request-URI field typically differ, as
       * the former contains a user name.  This address-of-record MUST
       * be a SIP URI or SIPS URI.
       */
      return getKeyToLocationService(address.getURI)
    }

    null
  }
}
