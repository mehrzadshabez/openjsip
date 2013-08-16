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

package openjsip.authentication

import java.security.{NoSuchAlgorithmException, MessageDigest}
import scala.{Byte, Long, Array}
import scala.Predef._
import javax.sip.message.Request
import javax.sip.address.URI
import javax.sip.header.AuthorizationHeader
import scala.util.Random

/**
 * The Digest Authentication Scheme implementation (see RFC 3261).
 * @param defaultRealm Realm to use when realm part is not specified in authentication headers.
 */
class DigestServerAuthentication(val defaultRealm: String) {

  private val random: Random = new Random(System.currentTimeMillis())
  private var algorithms: Map[String, MessageDigest] = Map[String, MessageDigest]()

  val preferredAlgorithm = "MD5"

  @throws[AuthenticationFailedException]
  private def getMessageDigest(algorithm: String) = {

    if (!algorithms.contains(algorithm)) {
      try {
        algorithms += algorithm -> MessageDigest.getInstance(algorithm)
      } catch {
        case ex: NoSuchAlgorithmException => throw new AuthenticationFailedException(algorithm + " is not supported")
      }
    }

    algorithms(algorithm)
  }

  /**
   * Generate the challenge string.
   * @param algorithm Encryption algorithm. "MD5", for example.
   * @return a generated nonce. Empty string if specified <i>algorithm</i> is not recognized.
   * @throws AuthenticationFailedException
   */
  @throws[AuthenticationFailedException]
  def generateNonce(algorithm: String): String = {
    val messageDigest = getMessageDigest(algorithm)
    val time: Long = System.currentTimeMillis
    val pad: Long = random.nextLong()
    val nonceString: String = time.toString + pad.toString
    val mdbytes: Array[Byte] = messageDigest.digest(nonceString.getBytes)
    toHexString(mdbytes)
  }

  /**
   * Actually performs authentication of subscriber.
   * @param authHeader Authroization header from the SIP request.
   * @param request Request to authorize
   * @param user Username to check with
   * @param password to check with
   * @return true if request is authorized, false in other case.
   * @throws AuthenticationFailedException
   */
  @throws[AuthenticationFailedException]
  def authenticate(request: Request, authHeader: AuthorizationHeader, user: String, password: String) {
    val username: String = authHeader.getUsername
    val uri: URI = authHeader.getURI

    if (username == null || !(username == user))
      throw new AuthenticationFailedException("Invalid username")
    if (uri == null)
      throw new AuthenticationFailedException("No URI present in authentication header")

    val realm: String = if (authHeader.getRealm != null) authHeader.getRealm else defaultRealm
    val algorithm: String = if (authHeader.getAlgorithm != null) authHeader.getAlgorithm else preferredAlgorithm

    val messageDigest: MessageDigest = getMessageDigest(algorithm)

    val A1: String = username + ":" + realm + ":" + password
    val A2: String = request.getMethod.toUpperCase + ":" + uri.toString
    val mdbytesA1 = messageDigest.digest(A1.getBytes)
    val HA1: String = toHexString(mdbytesA1)
    val mdbytesA2 = messageDigest.digest(A2.getBytes)
    val HA2: String = toHexString(mdbytesA2)

    val nonce: String = authHeader.getNonce
    val cnonce: String = authHeader.getCNonce
    var KD: String = HA1 + ":" + nonce

    if (cnonce != null) KD += ":" + cnonce
    KD += ":" + HA2

    val mdbytesKD = messageDigest.digest(KD.getBytes)

    val mdString: String = toHexString(mdbytesKD)
    val response: String = authHeader.getResponse

    val authenticationFailed = mdString.compareTo(response) != 0

    if (authenticationFailed)
      throw new AuthenticationFailedException("Invalid username or password specified")
  }


  private val HEX_NUMBERS = Array('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

  /**
   * @param b Array of bytes
   * @return A hexadecimal string
   */
  private def toHexString(b: Array[Byte]): String = {
    var pos = 0
    val c = new Array[Char](b.length * 2)

    for (i <- 0 until b.length) {
      c(pos) = HEX_NUMBERS(b(i) >> 4 & 0x0F)
      pos += 1
      c(pos) = HEX_NUMBERS(b(i) & 0x0f)
      pos += 1
    }

    new String(c)
  }
}
