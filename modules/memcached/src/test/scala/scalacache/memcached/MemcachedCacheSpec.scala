package scalacache.memcached

import org.scalatest._
import net.spy.memcached._
import scala.concurrent.duration._
import org.scalatest.concurrent.{ScalaFutures, Eventually, IntegrationPatience}
import org.scalatest.time.{Span, Seconds}

import scala.language.postfixOps
import scalacache.serialization.Codec
import scalacache.serialization.binary._
import cats.effect.IO

class MemcachedCacheSpec
    extends FlatSpec
    with Matchers
    with Eventually
    with BeforeAndAfter
    with BeforeAndAfterAll
    with ScalaFutures
    with IntegrationPatience {

  val client = new MemcachedClient(AddrUtil.getAddresses("localhost:11211"))

  override def afterAll() = {
    client.shutdown()
  }

  import scala.concurrent.ExecutionContext.Implicits.global

  def memcachedIsRunning = {
    try {
      client.get("foo")
      true
    } catch { case _: Exception => false }
  }

  def serialise[A](v: A)(implicit codec: Codec[A]): Array[Byte] =
    codec.encode(v)

  if (!memcachedIsRunning) {
    alert("Skipping tests because Memcached does not appear to be running on localhost.")
  } else {

    before {
      client.flush()
    }

    behavior of "get"

    it should "return the value stored in Memcached" in {
      client.set("key1", 0, serialise(123))
      whenReady(MemcachedCache[IO, Int](client).get("key1").unsafeToFuture()) {
        _ should be(Some(123))
      }
    }

    it should "return None if the given key does not exist in the underlying cache" in {
      whenReady(MemcachedCache[IO, Int](client).get("non-existent-key").unsafeToFuture()) {
        _ should be(None)
      }
    }

    behavior of "put"

    it should "store the given key-value pair in the underlying cache" in {
      whenReady(MemcachedCache[IO, Int](client).put("key2")(123, None).unsafeToFuture()) { _ =>
        client.get("key2") should be(serialise(123))
      }
    }

    behavior of "put with TTL"

    it should "store the given key-value pair in the underlying cache" in {
      whenReady(MemcachedCache[IO, Int](client).put("key3")(123, Some(3.seconds)).unsafeToFuture()) { _ =>
        client.get("key3") should be(serialise(123))

        // Should expire after 3 seconds
        eventually(timeout(Span(4, Seconds))) {
          client.get("key3") should be(null)
        }
      }
    }

    behavior of "remove"

    it should "delete the given key and its value from the underlying cache" in {
      client.set("key1", 0, 123)
      client.get("key1") should be(123)

      whenReady(MemcachedCache[IO, Int](client).remove("key1").unsafeToFuture()) { _ =>
        client.get("key1") should be(null)
      }
    }

  }

}
