package openjsip.datasource.subscriberdb

import openjsip.locationservice.{SubscriberProfile, LocationServiceKey}

trait SubscriberDatabase {
  def getProfile(key: LocationServiceKey): Option[SubscriberProfile]

  def contains(key: LocationServiceKey): Boolean

  def add(key: LocationServiceKey, profile: SubscriberProfile)

  def remove(key: LocationServiceKey)

  def iterate(): Iterable[SubscriberProfile]
}