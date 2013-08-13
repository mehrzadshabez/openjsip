package openjsip.locationservice

import org.scalatest.{BeforeAndAfter, FunSuite}
import openjsip.locationservice.exceptions.{SubscriberNotFoundException, SubscriberAlreadyExists}
import javax.sip.SipFactory
import javax.sip.header.HeaderFactory
import javax.sip.address.AddressFactory
import scala.util.Random
import openjsip.datasource.subscriberdb.{SubscriberDatabaseImpl, SubscriberDatabase}

class LocationServiceImplTest extends FunSuite with BeforeAndAfter {

  var locationService: LocationServiceImpl = _
  var database: SubscriberDatabase = _

  val headerFactory: HeaderFactory = SipFactory.getInstance().createHeaderFactory()
  val addressFactory: AddressFactory = SipFactory.getInstance().createAddressFactory()

  before {
    database = new SubscriberDatabaseImpl()
    locationService = new LocationServiceImpl()
    locationService.setSubscriberDatabase(database)
  }

  test("Must be able to register subscribers") {
    val key = locationService.registerSubscriber("sip:1000")
    assert(locationService.getProfile(key) !== null)
  }

  test("Must be able to unregister subscribers") {
    val key = locationService.registerSubscriber("sip:1000")
    assert(locationService.getProfile(key) !== null)

    locationService.unregisterSubscriber("sip:1000")

    intercept[SubscriberNotFoundException] {
      locationService.getProfile(key)
    }
  }

  test("Must throw exception when trying to register a subscriber that is already registered") {
    intercept[SubscriberAlreadyExists] {
      locationService.registerSubscriber("sip:1000")
      locationService.registerSubscriber("sip:1000")
    }
  }

  test("URIs are case insensitive") {
    intercept[SubscriberAlreadyExists] {
      locationService.registerSubscriber("sip:user1")
      locationService.registerSubscriber("sip:USER1")
    }
  }

  test("Get subscriber login and password") {
    val key = locationService.registerSubscriber("sip:login:password@openjsip.net")
    val profile = locationService.getProfile(key)
    assert(profile.getLogin === "login")
    assert(profile.getPassword === "password")
  }

  test("Must accept subscriber registrations") {
    val key = locationService.registerSubscriber("sip:1000")
    val expectedCallId = Random.nextString(5) + '@' + Random.nextString(5)
    val expectedExpires = Random.nextInt()
    val expectedCseq = Random.nextInt()

    val address = addressFactory.createAddress("sip:alice@pc33.atlanta.com")
    val contactHeader = headerFactory.createContactHeader(address)

    locationService.updateRegistration(key, contactHeader, expectedExpires, expectedCallId, expectedCseq)

    val binding = locationService.getBinding(key, contactHeader)

    assert(binding !== null)
    assert(binding.expiresTime === expectedExpires)
    assert(binding.callId === expectedCallId)
    assert(binding.cseq === expectedCseq)
  }

  test("Must update subscriber registrations") {

    val key = locationService.registerSubscriber("sip:1000")
    val address = addressFactory.createAddress("sip:alice@pc33.atlanta.com")
    val contactHeader = headerFactory.createContactHeader(address)

    val initialCallId = Random.nextString(5) + '@' + Random.nextString(5)
    val initialExpires = Random.nextInt()
    val initialCseq = Random.nextInt()

    locationService.updateRegistration(key, contactHeader, initialExpires, initialCallId, initialCseq)

    val newCallId = Random.nextString(5) + '@' + Random.nextString(5)
    val newExpires = Random.nextInt()
    val newCseq = Random.nextInt()

    locationService.updateRegistration(key, contactHeader, newExpires, newCallId, newCseq)


    val binding = locationService.getBinding(key, contactHeader)

    assert(binding !== null)
    assert(binding.expiresTime === newExpires)
    assert(binding.callId === newCallId)
    assert(binding.cseq === newCseq)
  }

  test("Must remove expired bindings") {

    val testExpirationTime = 1

    val key = locationService.registerSubscriber("sip:1000")

    val address1 = addressFactory.createAddress("sip:alice@pc33.atlanta.com")
    val address2 = addressFactory.createAddress("sip:bob@pc33.atlanta.com")
    val contactHeaderToBeExpired = headerFactory.createContactHeader(address1)
    val contactHeaderNotToBeExpired = headerFactory.createContactHeader(address2)

    val expires1 = testExpirationTime
    val callId1 = Random.nextString(5) + '@' + Random.nextString(5)
    val cseq1 = Random.nextInt()
    val expires2 = testExpirationTime * 5
    val callId2 = Random.nextString(5) + '@' + Random.nextString(5)
    val cseq2 = Random.nextInt()

    locationService.updateRegistration(key, contactHeaderToBeExpired, expires1, callId1, cseq1)
    locationService.updateRegistration(key, contactHeaderNotToBeExpired, expires2, callId2, cseq2)

    val contactHeadersBeforeExpirationCheck = locationService.getContactHeaders(key)
    assert(contactHeadersBeforeExpirationCheck.containsSlice(List(contactHeaderToBeExpired, contactHeaderNotToBeExpired)))

    locationService.setBindingsExpirationCheckPeriod(expires1)

    Thread.sleep(testExpirationTime * 1000 * 2)

    val contactHeadersAfterExpirationCheck = locationService.getContactHeaders(key)
    assert(contactHeadersAfterExpirationCheck.contains(contactHeaderToBeExpired) === false)
    assert(contactHeadersAfterExpirationCheck.contains(contactHeaderNotToBeExpired))
  }
}