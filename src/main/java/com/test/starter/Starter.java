package com.test.starter;

import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.test.client.ReconnectingClient;
import com.test.server.NopServer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

public class Starter {

    private static final Logger logger = LoggerFactory.getLogger(Starter.class);

    public static void main(String[] args) {
        logger.info("Starting up actor systems");
        Config conf = ConfigFactory.load();

        Config confClient = conf.withValue("akka.remote.netty.tcp.port", ConfigValueFactory.fromAnyRef(0));
        Config confServer = conf;

        ActorSystem clientSystem = ActorSystem.create("clientSystem", confClient);
        ActorRef reconnectingClient = clientSystem.actorOf(Props.create(ReconnectingClient.class), "reconnectingClient");

        ActorSystem serverSystem = ActorSystem.create("serverSystem", confServer);
        ActorRef nopServer= serverSystem.actorOf(Props.create(NopServer.class), "nopServer");
    }
}
