package com.koadr;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;

import java.util.Random;

/**
 * Created by craigpottinger on 2/2/17.
 */
public class App {
    private static String getRandom(String[] array) {
        int rnd = new Random().nextInt(array.length);
        return array[rnd];
    }

    public static void main(String [] args) {
        final ActorSystem system = ActorSystem.create("StreamSystem");
        final ActorMaterializer materializer = ActorMaterializer.create(system);
        ActorRef emojiActor = system.actorOf(EmojiActor.props());
        ActorRef translatorActor = system.actorOf(EnglishToSpanishActor.props());
        ActorRef streamer = system.actorOf(SagaStreamActor.props(emojiActor, translatorActor, materializer));
        streamer.tell(SagaStreamActor.Start.getInstance(), ActorRef.noSender());
        int counter = 100;
        String[] words = new String[]{"hello", "tired", "goodbye"};
        while (counter > 0) {
            ActorRef saga = system.actorOf(SagaActor.props(streamer));
            saga.tell(getRandom(words), ActorRef.noSender());
            counter--;
        }

    }



}
