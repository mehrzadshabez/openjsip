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
import openjsip.locationservice.exceptions.SubscriberNotFoundException

trait LocationService {

  /**
   * @param key Key to location service directory
   * @throws SubscriberNotFoundException if no subscriber is associated with <i>key</i>
   * @return Subscriber profile if found
   */
  @throws[SubscriberNotFoundException]
  def getProfile(key: LocationServiceKey): SubscriberProfile

  /**
   * Create or update binding for subscriber.
   * @param key Key to location service directory
   * @param contactHeader Contact address that is associated with the subscriber <i>key</i> for the duration of <i>expires</i>
   * @param expires Time-to-live value for this binding measured in seconds.
   * @param callId CallID parameter from REGISTER request
   * @param cseq CSeQ parameter from REGISTER request
   * @throws SubscriberNotFoundException If the subscriber specified by <i>key</i> cannot be found
   */
  @throws[SubscriberNotFoundException]
  def updateRegistration(key: LocationServiceKey, contactHeader: ContactHeader, expires: Long, callId: String, cseq: Long): Unit

  /**
   * Removes one contact address binding of subscriber.
   * @param key Key to location service directory
   * @param contactHeader Contact address that is associated with the subscriber <i>key</i>.
   * @throws SubscriberNotFoundException If the subscriber specified by <i>key</i> cannot be found
   */
  @throws[SubscriberNotFoundException]
  def removeBinding(key: LocationServiceKey, contactHeader: ContactHeader)

  /**
   * Removes all contact address bindings of subscriber.
   * @param key Key to location service directory
   * @throws SubscriberNotFoundException If the subscriber specified by <i>key</i> cannot be found
   */
  @throws[SubscriberNotFoundException]
  def removeAllBindings(key: LocationServiceKey)

  /**
   * @param key Key to location service directory
   * @return Returns all contact addresses associated with the subscriber.
   * @throws SubscriberNotFoundException If the subscriber specified by <i>key</i> cannot be found
   */
  @throws[SubscriberNotFoundException]
  def getContactHeaders(key: LocationServiceKey): List[ContactHeader]

  /**
   * @param key Key to location service directory
   * @param contactHeader Contact address that is associated with the subscriber <i>key</i>.
   * @return Returns the binding of subscriber whose contact address matches <i>contactHeader</i>, null otherwise.
   * @throws SubscriberNotFoundException If the subscriber specified by <i>key</i> cannot be found
   */
  @throws[SubscriberNotFoundException]
  def getBinding(key: LocationServiceKey, contactHeader: ContactHeader): Binding
}

object LocationService {

  /**
   * The URI
   * MUST then be converted to a canonical form.  To do that, all
   * URI parameters MUST be removed (including the user-param), and
   * any escaped characters MUST be converted to their unescaped
   * form.  The result serves as an index into the list of bindings.
   * @param uri URI to make key value from.
   * @return The key value to use with location service database. Key is needed to find current location of subscriber by <i>uri</i>.
   */
  def getKeyToLocationService(uri: URI): LocationServiceKey = {

    require(uri.isSipURI)

    val canonicalizedUri: URI = SipUtils.getCanonicalizedURI(uri)
    val sipUri: SipUri = canonicalizedUri.asInstanceOf[SipUri]

    sipUri.clearPassword()
    sipUri.removePort()
    sipUri.clearQheaders()

    new LocationServiceKey(sipUri.toString.toLowerCase)
  }

  /**
   * @param request Original request
   * @return The key value to use with location service database. Key is needed to find current location of subscriber specified in To header of <i>request</i>. Returns null if To header is not SIP URI like.
   */
  def getKeyToLocationService(request: Request): LocationServiceKey = {
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
