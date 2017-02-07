package com.koadr;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;

/**
 * Created by craigpottinger on 2/2/17.
 */
public class App {

    public static void main(String [] args) {
        final ActorSystem system = ActorSystem.create("StreamSystem");
        final ActorMaterializer materializer = ActorMaterializer.create(system);
        ActorRef emojiActor = system.actorOf(EmojiActor.props());
        ActorRef translatorActor = system.actorOf(EnglishToSpanishActor.props());
        ActorRef streamer = system.actorOf(SagaStreamActor.props(emojiActor, translatorActor, materializer));
        streamer.tell(SagaStreamActor.Start.getInstance(), ActorRef.noSender());
        int counter = 100;
        while (counter > 0) {
            ActorRef saga = system.actorOf(SagaActor.props(streamer));
            saga.tell("hello", ActorRef.noSender());
            counter--;
        }

    }



}
