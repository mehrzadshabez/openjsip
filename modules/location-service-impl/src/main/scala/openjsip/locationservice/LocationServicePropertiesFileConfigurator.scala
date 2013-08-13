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

package openjsip.locationservice

import java.util.Properties
import javax.sip.address.{URI, AddressFactory}
import javax.sip.SipFactory
import java.io.FileInputStream
import gov.nist.javax.sip.address.SipUri
import org.apache.log4j.Logger


class LocationServicePropertiesFileConfigurator(props: Properties) {

  private val log: Logger = Logger.getLogger("")

  private val addressFactory: AddressFactory = SipFactory.getInstance().createAddressFactory()

  private var domains = scala.collection.mutable.Set[String]()

  private var database = Map[String, SubscriberProfile]()

  private var defaultDomain = ""

  private var updatePeriod = 0

  def init() {

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
            val locationServiceKey = LocationService.getKeyToLocationService(addressOfRecord)

            database += (key -> new SubscriberProfile(addressOfRecord))
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
    updatePeriod = props.getProperty("location.service.update-period", "5").trim.toInt

    // Update period must be > 0
    updatePeriod = Math.max(1, updatePeriod)

    if (log.isInfoEnabled)
      log.info("Bindings refresh period: " + updatePeriod + " seconds.")

  }

  def configure(locationService: LocationServiceManagement) {
    database.foreach(f => locationService.registerSubscriber(f._1))
  }
}
