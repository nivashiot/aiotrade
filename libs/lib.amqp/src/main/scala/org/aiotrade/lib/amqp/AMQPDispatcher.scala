package org.aiotrade.lib.amqp

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Consumer
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import com.rabbitmq.client.ShutdownListener
import com.rabbitmq.client.ShutdownSignalException
import java.io.IOException
import java.util.Timer
import java.util.TimerTask
import java.util.logging.Logger
import java.util.logging.Level
import org.aiotrade.lib.amqp.datatype.ContentType
import scala.actors.Actor

/*_ rabbitmqctl common usages:
 sudo rabbitmq-server -n rabbit@localhost &
 sudo rabbitmqctl -n rabbit@localhost stop

 sudo rabbitmqctl -n rabbit@localhost stop_app
 sudo rabbitmqctl -n rabbit@localhost reset
 sudo rabbitmqctl -n rabbit@localhost start_app
 sudo rabbitmqctl -n rabbit@localhost list_queues name messages messages_uncommitted messages_unacknowledged

 If encountered troubes when start the server up, since the tables in the mnesia
 database backing rabbitmq are locked (Don't know why this is the case). you can
 get this running again brute force styleee by deleting the database:

 sudo rm -rf /opt/local/var/lib/rabbitmq/mnesia/
 */

/*_
 * Option 1:
 * create one queue per consumer with several bindings, one for each stock.
 * Prices in this case will be sent with a topic routing key.
 *
 * Option 2:
 * Another option is to create one queue per stock. each consumer will be
 * subscribed to several queues. Messages will be sent with a direct routing key.
 *
 * Best Practice:
 * Option 1: should work fine, except there is no need to use a topic exchange.
 * Just use a direct exchange, one queue per user and for each of the stock
 * symbols a user is interested in create a binding between the user's queue
 * and the direct exchange.
 *
 * Option 2: each quote would only go to one consumer, which is probably not
 * what you want. In an AMQP system, to get the same message delivered to N
 * consumers you need (at least) N queues. Exchanges *copy* messages to queues,
 * whereas queues *round-robin* message delivery to consumers.
 */

/**
 * @param content A deserialized value received via AMQP.
 * @param props
 *
 * Messages received from AMQP are wrapped in this case class. When you
 * register a listener, this is the case class that you will be matching on.
 */
case class AMQPMessage(content: Any, props: AMQP.BasicProperties)

case class AMQPPublish(routingKey: String, props: AMQP.BasicProperties, content: Any)

/**
 * @param a The actor to add as a Listener to this Dispatcher.
 */
case class AMQPAddListener(a: Actor)

case object AMQPConnect
/**
 * Reconnect to the AMQP Server after a delay of {@code delay} milliseconds.
 */
case class AMQPReconnect(delay: Long)

case object AMQPStop

object AMQPExchange {
  /**
   * Each AMQP broker declares one instance of each supported exchange type on it's
   * own (for every virtual host). These exchanges are named after the their type
   * with a prefix of amq., e.g. amq.fanout. The empty exchange name is an alias
   * for amq.direct. For this default direct exchange (and only for that) the broker
   * also declares a binding for every queue in the system with the binding key
   * being identical to the queue name.
   *
   * This behaviour implies that any queue on the system can be written into by
   * publishing a message to the default direct exchange with it's routing-key
   * property being equal to the name of the queue.
   */
  val defaultDirect = "" // amp.direct
}

/**
 * The dispatcher that listens over the AMQP message endpoint.
 * It manages a list of subscribers to the trade message and also sends AMQP
 * messages coming in to the queue/exchange to the list of observers.
 */
abstract class AMQPDispatcher(factory: ConnectionFactory, val exchange: String) extends Actor with Serializer {

  private val log = Logger.getLogger(this.getClass.getName)

  private var listeners: List[Actor] = Nil

  case class State(conn: Connection, channel: Channel, consumer: Option[Consumer])
  var state: State = _

  /**
   * Connect only when start, so we can control it to connect at a appropriate time,
   * for instance, all processors are ready. Otherwise, the messages may have been
   * consumered before processors ready.
   */
  @throws(classOf[IOException])
  override def start: this.type = {
    super.start
    //asyncConnet
    try {
      state = connect
    } catch {
      case ex => 
        log.log(Level.WARNING, ex.getMessage, ex)
        reconnect(5000)
    }

    if (channel != null) {
      channel.addShutdownListener(new ShutdownListener {
          def shutdownCompleted(cause: ShutdownSignalException) {
            reconnect(5000)
          }
        })
    }
    this
  }

  protected def conn = state.conn
  protected def channel = state.channel
  protected def consumer = state.consumer

  /** Pending ... or, is it necessary? */
  @throws(classOf[IOException])
  private def asyncConnet {
    (this !? AMQPConnect) match {
      case x: State => state = x
      case x: Throwable => log.log(Level.SEVERE, x.getMessage, x); throw x
      case x => log.severe("Error during amqp connect: " + x); throw new Exception(x.toString)
    }
  }

  @throws(classOf[IOException])
  private def connect: State = {
    val conn = factory.newConnection
    val channel = conn.createChannel
    val consumer = configure(channel)
    State(conn, channel, consumer)
  }

  /**
   * Registers queue and consumer.
   * @throws IOException if an error is encountered
   * @return the newly created and registered (queue, consumer)
   */
  @throws(classOf[IOException])
  protected def configure(channel: Channel): Option[Consumer]

  def act = loop {
    react {
      case AMQPConnect => reply(connect)
      case AMQPReconnect(delay) => reconnect(delay)
      case msg: AMQPMessage =>
        listeners foreach (_ ! msg)
      case AMQPAddListener(l) =>
        listeners ::= l
      case AMQPPublish(routingKey, props, content) => publish(exchange, routingKey, props, content)
      case AMQPStop =>
        disconnect
        listeners foreach (_ ! AMQPStop)
        exit
    }
  }

  def addListener(l: Actor) {
    listeners ::= l
  }

  @throws(classOf[IOException])
  protected def publish(exchange: String, routingKey: String, $props: AMQP.BasicProperties, content: Any) {
    import ContentType._

    val props = if ($props == null) new AMQP.BasicProperties else $props

    val contentType = props.getContentType match {
      case null | "" => JAVA_SERIALIZED_OBJECT
      case x => ContentType(x)
    }

    val body = contentType.mimeType match {
      case OCTET_STREAM.mimeType => content.asInstanceOf[Array[Byte]]
      case JAVA_SERIALIZED_OBJECT.mimeType => encodeJava(content)
      case JSON.mimeType => encodeJson(content)
      case _ => encodeJava(content)
    }

    val contentEncoding = props.getContentEncoding match {
      case null | "" => props.setContentEncoding("gzip"); "gzip"
      case x => x
    }
    
    val body1 = contentEncoding match {
      case "gzip" => gzip(body)
      case "lzma" => lzma(body)
      case _ => body
    }

    //println(content + " sent: routingKey=" + routingKey + " size=" + body.length)
    channel.basicPublish(exchange, routingKey, props, body1)
  }

  protected def disconnect {
    if (consumer.isDefined && channel != null) {
      channel.basicCancel(consumer.get.asInstanceOf[DefaultConsumer].getConsumerTag)
    }

    if (channel != null) {
      try {
        channel.close
      } catch {
        case e: IOException => log.log(Level.INFO, "Could not close AMQP channel %s:%s [%s]", Array(factory.getHost, factory.getPort, this))
        case _ => ()
      }
    }

    if (conn != null) {
      try {
        conn.close
        log.log(Level.FINEST, "Disconnected AMQP connection at %s:%s [%s]", Array(factory.getHost, factory.getPort, this))
      } catch {
        case e: IOException => log.log(Level.WARNING, "Could not close AMQP connection %s:%s [%s]", Array(factory.getHost, factory.getPort, this))
        case _ => ()
      }
    }
  }

  protected def reconnect(delay: Long) {
    disconnect
    try {
      state = connect
      log.log(Level.FINEST, "Successfully reconnected to AMQP Server %s:%s [%s]", Array(factory.getHost, factory.getPort, this))
    } catch {
      case e: Exception =>
        val waitInMillis = delay * 2
        val self = this
        log.log(Level.FINEST, "Trying to reconnect to AMQP server in %n milliseconds [%s]", Array(waitInMillis, this))
        new Timer("AMQPReconnectTimer").schedule(new TimerTask {
            def run {
              self ! AMQPReconnect(waitInMillis)
            }
          }, delay)
    }
  }

  class AMQPConsumer(channel: Channel) extends DefaultConsumer(channel) {
    private val log = Logger.getLogger(this.getClass.getName)

    @throws(classOf[IOException])
    override def handleDelivery(tag: String, env: Envelope, props: AMQP.BasicProperties, body: Array[Byte]) {
      import ContentType._

      log.info("Got amqp message: " + (body.length / 1024.0) + "k" )

      val body1 = props.getContentEncoding match {
        case "gzip" => ungzip(body)
        case "lzma" => unlzma(body)
        case _ => body
      }

      val contentType = props.getContentType match {
        case null | "" => JAVA_SERIALIZED_OBJECT
        case x => ContentType(x)
      }

      val content = contentType.mimeType match {
        case OCTET_STREAM.mimeType => body1
        case JAVA_SERIALIZED_OBJECT.mimeType => decodeJava(body1)
        case JSON.mimeType => decodeJson(body1)
        case _ => decodeJava(body1)
      }

      log.info("Decoded amqp message.")

      // send back to interested observers for further relay
      val msg = AMQPMessage(content, props)
      listeners foreach (_ ! msg)

      // if noAck is set false, messages will be blocked until an ack to broker,
      // so it's better always ack it. (Although prefetch may deliver more than
      // one message to consumer)
      channel.basicAck(env.getDeliveryTag, false)
    }
  }
}
