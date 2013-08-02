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
package openjsip

import gov.nist.javax.sip.address.SipUri
import javax.sip.address.URI

object SipUtils {

  /**
   * If URI is the SIP URI, method returns its clone without any parameters,
   * otherwise original URI is returned without any modifications.
   * @param uri URI
   * @return Canonicalized URI ( without parameters )
   */
  def getCanonicalizedURI(uri: URI): URI = {
    if (uri != null && uri.isSipURI) {
      val sipUri: SipUri = uri.asInstanceOf[SipUri].clone().asInstanceOf[SipUri]
      sipUri.clearUriParms()
      sipUri
    }
    else {
      uri
    }
  }
}
