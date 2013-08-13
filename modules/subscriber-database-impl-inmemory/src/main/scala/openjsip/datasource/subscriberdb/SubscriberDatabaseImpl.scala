package openjsip.datasource.subscriberdb

import openjsip.locationservice.{SubscriberProfile, LocationServiceKey}

class SubscriberDatabaseImpl extends SubscriberDatabase {

  private var database = Map[LocationServiceKey, SubscriberProfile]()

  def add(key: LocationServiceKey, profile: SubscriberProfile) {
    database += (key -> profile)
  }

  def contains(key: LocationServiceKey): Boolean = {
    database.contains(key)
  }

  def getProfile(key: LocationServiceKey): Option[SubscriberProfile] = {
    database.get(key)
  }

  def iterate(): Iterable[SubscriberProfile] = {
    database.values
  }

  def remove(key: LocationServiceKey) {
    database -= key
  }
}
