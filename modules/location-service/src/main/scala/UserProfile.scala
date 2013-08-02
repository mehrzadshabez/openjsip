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

import javax.sip.address.SipURI
import javax.sip.header.ContactHeader
import openjsip.SipUtils

/**
 * @param addressOfRecord An address-of-record (AOR) is a SIP or SIPS URI
 *                        that points to a domain with a location service that can map
 *                        the URI to another URI where the user might be available.
 *                        Typically, the location service is populated through
 *                        registrations.  An AOR is frequently thought of as the "public
 *                        address" of the user.
 */
class UserProfile(addressOfRecord: SipURI) {

  /**
   * The list of bindings of this subscriber.
   */
  private var bindings = Set[Binding]()

  /**
   * @return Login (username) of subscriber
   */
  def getLogin = {
    addressOfRecord.getUser
  }

  /**
   * @return Password of subscriber, or null if not specified.
   */
  def getPassword = {
    addressOfRecord.getUserPassword
  }

  def getBindings = {
    bindings
  }

  /**
   * @param contactHeader Contact header
   * @return The existing binding whose contact header matches <i>contactHeader</i>
   */
  def getBinding(contactHeader: ContactHeader): Binding = {

    val canonicalizedUri1 = SipUtils.getCanonicalizedURI(contactHeader.getAddress.getURI)

    bindings.find {
      b =>
        val canonicalizedUri2 = SipUtils.getCanonicalizedURI(b.contactHeader.getAddress.getURI)
        canonicalizedUri1 == canonicalizedUri2
    }.orNull
  }

  /**
   * @return The list of contact headers from all bindings of this subscriber.
   */
  def getContactHeaders: Set[ContactHeader] = {
    for (b <- bindings)
    yield b.contactHeader
  }

  /**
   * Adds a binding to the current list of bindings
   * @param binding Binding to add
   */
  def addBinding(binding: Binding) {
    bindings += binding
  }

  /**
   * Removes the specified binding, if found.
   * @param binding Binding to remove.
   */
  def removeBinding(binding: Binding) {
    bindings -= binding
  }

  /**
   * Removes all bindings of this subscriber.
   */
  def removeAllBindings() {
    bindings = Set[Binding]()
  }

}