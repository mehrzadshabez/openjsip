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

import java.util.{TimerTask, Timer, Properties}
import java.io.FileInputStream
import org.apache.log4j.Logger
import Predef._
import javax.sip.header.ContactHeader
import javax.sip.address.{URI, AddressFactory}
import javax.sip.SipFactory
import scala.Predef.String
import gov.nist.javax.sip.address.SipUri

class LocationServiceImpl(props: Properties) extends LocationService {

  val log: Logger = null

  /**
   * Users database
   */
  var database = Map[String, UserProfile]()

  /**
   * Default domain
   */
  var defaultDomain: String = _

  /**
   * Set of responsible domains
   */
  var domains = Set[String]()

  /**
   * Timer that check binding for expiration
   */
  var checkBindingsTimer: Timer = _

  /**
   * The previous time when check bindings task was run
   */
  var prevCheckTime: Long = _


  if (log.isInfoEnabled)
    log.info("Starting Location Service server")

  val addressFactory: AddressFactory = SipFactory.getInstance().createAddressFactory()

  // Set default domain
  defaultDomain = props.getProperty("location.service.default.domain", "openjsip.net").trim().toLowerCase

  log.info("Default domain: " + defaultDomain)
  log.info("Reading user database...")

  val dbPath = props.getProperty("location.service.db.file")
  val db = new Properties()

  if (dbPath != null) {
    db.load(new FileInputStream(dbPath))
  }

  var maxUsers = db.getProperty("user.max", "100").trim.toInt

  /**
   * NOTE: Address-of-Record: An address-of-record (AOR) is a SIP or SIPS URI
   * that points to a domain with a location service that can map
   * the URI to another URI where the user might be available.
   * Typically, the location service is populated through
   * registrations.  An AOR is frequently thought of as the "public
   * address" of the user.
   */
  var numSkipped = 0


  for (index <- 1 to maxUsers) {
    var key = db.getProperty("user." + index)
    if (key != null) {

      key = key.trim()

      val domainPos = key.indexOf('@')
      if (domainPos == -1) key = key.concat("@" + defaultDomain)

      try {
        val uri: URI = addressFactory.createURI(key)

        if (uri.isSipURI) {

          val addressOfRecord: SipUri = uri.asInstanceOf[SipUri]
          addressOfRecord.setHost(addressOfRecord.getHost.toLowerCase)

          // Add domain to domain list
          domains += addressOfRecord.getHost

          /**
           * The URI
           * MUST then be converted to a canonical form.  To do that, all
           * URI parameters MUST be removed (including the user-param), and
           * any escaped characters MUST be converted to their unescaped
           * form.  The result serves as an index into the list of bindings.
           */
          key = getKeyToLocationService(addressOfRecord)

          database += (key -> new UserProfile(addressOfRecord))
        }
        else {
          numSkipped += 1
        }
      }
      catch {
        case ex: Exception =>
          numSkipped += 1
      }
    }
  }

  /**
   * Print records count
   */
  if (log.isInfoEnabled) {

    for (domain <- domains) {
      var count = 0

      for (userAtHost <- database.keys) {
        if (userAtHost.substring(userAtHost.indexOf('@') + 1).equals(domain)) {
          count += 1
        }
      }

      log.info("Domain " + domain + " contains " + count + " records.")
    }

    if (numSkipped > 0)
      log.info("Skipped " + numSkipped + " records.")

  }

  // Default update period - 5 sec.
  var updatePeriod = props.getProperty("location.service.update-period", "5").trim.toInt

  // Update period must be > 0
  updatePeriod = Math.max(1, updatePeriod)

  /**
   * Create timer. This timer will check all bindings and delete those that are expired.
   */
  checkBindingsTimer = new Timer()


  prevCheckTime = System.currentTimeMillis

  checkBindingsTimer.schedule(new CheckBindingsTask(), 0, updatePeriod * 1000)



  if (log.isInfoEnabled)
    log.info("Bindings refresh period: " + updatePeriod + " seconds.")

  // Add shutdown hook
  sys.addShutdownHook(shutdownHook())


  def getBinding(key: String, contactHeader: ContactHeader): Binding = {
    getProfile(key).getBinding(contactHeader)
  }

  def getContactHeaders(key: String): List[ContactHeader] = getProfile(key).getContactHeaders.toList


  def getDefaultDomain: String = defaultDomain


  def getDomains: Set[String] = domains


  def getPassword(key: String): String = getProfile(key).getPassword


  def getUsername(key: String): String = getProfile(key).getLogin


  def removeAllBindings(key: String) {
    getProfile(key).removeAllBindings()
  }


  def removeBinding(key: String, contactHeader: ContactHeader) {

    val userProfile: UserProfile = getProfile(key)
    val existingBinding: Binding = userProfile.getBinding(contactHeader)

    if (existingBinding != null) {
      userProfile.removeBinding(existingBinding)
      if (log.isDebugEnabled)
        log.debug("Binding removed ( " + key + " ): " + existingBinding.toString)
    }
  }


  def updateRegistration(key: String, contactHeader: ContactHeader, expires: Long, callId: String, cseq: Long) {

    val userProfile = getProfile(key)
    val existingBinding: Binding = userProfile.getBinding(contactHeader)

    if (existingBinding != null)
      userProfile.removeBinding(existingBinding)

    val newBinding = new Binding(key, contactHeader, callId, cseq, expires)

    userProfile.addBinding(newBinding)

    if (log.isDebugEnabled)
      log.debug("Binding updated ( " + key + " ): " + newBinding)
  }

  def isAlive: Boolean = true

  def shutdownHook() {
    if (checkBindingsTimer != null)
      checkBindingsTimer.cancel()
  }

  /**
   * Returns user profile
   * @param key Key to location service
   * @return User profile
   * @throws UserNotFoundException If such user cannot be found
   */
  private def getProfile(key: String): UserProfile = {
    val userProfile = database(key)
    if (userProfile == null) throw new UserNotFoundException(key)
    userProfile
  }

  /**
   * Task that runs every "location.service.update-period" seconds and checks
   * all existing bindings for being expired.
   * Expired bindings are removed from user profile.
   */
  private class CheckBindingsTask extends TimerTask {
    override def run() {

      val dt = (System.currentTimeMillis() - prevCheckTime) / 1000

      prevCheckTime = System.currentTimeMillis()

      database.values.foreach(profile => {

        val bindings = profile.getBindings

        bindings.foreach(binding =>
          binding.expiresTime = binding.expiresTime - dt.toInt
        )

        val expiredBindings = bindings.filter(b => b.expiresTime <= 0)
        for (b <- expiredBindings)
          profile.removeBinding(b)
      })
    }
  }

}