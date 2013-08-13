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

import java.util.{TimerTask, Timer}
import org.apache.log4j.Logger
import javax.sip.header.ContactHeader
import javax.sip.address.{SipURI, AddressFactory}
import javax.sip.SipFactory
import scala.Predef.String
import openjsip.locationservice.exceptions.{SubscriberAlreadyExists, SubscriberNotFoundException}
import openjsip.datasource.subscriberdb.SubscriberDatabase

class LocationServiceImpl extends LocationService with LocationServiceManagement {

  private val log: Logger = Logger.getLogger(LocationService.getClass)

  private val addressFactory: AddressFactory = SipFactory.getInstance().createAddressFactory()

  /**
   * Users database
   */
  private var database: SubscriberDatabase = _

  /**
   * Timer that check binding for expiration
   */
  private var checkBindingsTimer: Timer = _

  /**
   * The previous time when check bindings task was run
   */
  private var prevCheckTime: Long = _


  override def getProfile(key: LocationServiceKey): SubscriberProfile = {
    database.getProfile(key).getOrElse(throw new SubscriberNotFoundException(key))
  }

  override def getBinding(key: LocationServiceKey, contactHeader: ContactHeader): Binding = {
    getProfile(key).getBinding(contactHeader)
  }

  override def getContactHeaders(key: LocationServiceKey): List[ContactHeader] = getProfile(key).getContactHeaders.toList


  override def removeAllBindings(key: LocationServiceKey) {
    getProfile(key).removeAllBindings()
  }

  override def removeBinding(key: LocationServiceKey, contactHeader: ContactHeader) {

    val profile: SubscriberProfile = getProfile(key)
    val existingBinding: Binding = profile.getBinding(contactHeader)

    if (existingBinding != null) {
      profile.removeBinding(existingBinding)
      if (log.isDebugEnabled)
        log.debug("Binding removed ( " + key + " ): " + existingBinding.toString)
    }
  }

  override def updateRegistration(key: LocationServiceKey, contactHeader: ContactHeader, expires: Long, callId: String, cseq: Long) {

    val profile = getProfile(key)
    val existingBinding: Binding = profile.getBinding(contactHeader)

    if (existingBinding != null)
      profile.removeBinding(existingBinding)

    val newBinding = new Binding(key, contactHeader, callId, cseq, expires)

    profile.addBinding(newBinding)

    if (log.isDebugEnabled)
      log.debug("Binding updated ( " + key + " ): " + newBinding)
  }

  override def registerSubscriber(addressOfRecord: String): LocationServiceKey = {
    val uri = addressFactory.createURI(addressOfRecord)
    val key = LocationService.getKeyToLocationService(uri)
    registerSubscriber(key, new SubscriberProfile(uri.asInstanceOf[SipURI]))
    key
  }

  override def unregisterSubscriber(addressOfRecord: String) {
    val uri = addressFactory.createURI(addressOfRecord)
    val key = LocationService.getKeyToLocationService(uri)
    unregisterSubscriber(key)
  }

  override def setBindingsExpirationCheckPeriod(seconds: Int) {

    if (checkBindingsTimer != null) {
      checkBindingsTimer.cancel()
    }

    /**
     * Create timer. This timer will check all bindings and delete those that are expired.
     */
    checkBindingsTimer = new Timer()

    prevCheckTime = System.currentTimeMillis

    checkBindingsTimer.schedule(new TimerTask() {
      def run() = removeExpiredBindings()
    }, 0, seconds * 1000)
  }

  def setSubscriberDatabase(database: SubscriberDatabase) {
    this.database = database
  }

  private def registerSubscriber(key: LocationServiceKey, profile: SubscriberProfile) {
    if (database.contains(key))
      throw new SubscriberAlreadyExists(key)

    database.add(key, profile)
  }

  private def unregisterSubscriber(key: LocationServiceKey) {
    database.remove(key)
  }

  private def removeExpiredBindings() {
    val dt = (System.currentTimeMillis() - prevCheckTime) / 1000

    prevCheckTime = System.currentTimeMillis()

    database.iterate().foreach(profile => {

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