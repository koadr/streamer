package com.koadr;


import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import static com.koadr.SagaStreamActor.Translation;

import java.util.Optional;

import static com.koadr.SagaStreamActor.Word;

public class SagaActor extends AbstractLoggingActor {

    public static class AddEmoji implements Message {
        final String word;
        final String emoji;

        public AddEmoji(String word, String emoji) {
            this.word = word;
            this.emoji = emoji;
        }

        public String getWord() {
            return word;
        }

        public String getEmoji() {
            return emoji;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AddEmoji)) return false;

            AddEmoji addEmoji = (AddEmoji) o;

            if (!word.equals(addEmoji.word)) return false;
            return emoji.equals(addEmoji.emoji);
        }

        @Override
        public int hashCode() {
            int result = word.hashCode();
            result = 31 * result + emoji.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "AddEmoji{" +
                    "word='" + word + '\'' +
                    ", emoji='" + emoji + '\'' +
                    '}';
        }
    }

    public static class AddTranslation implements Message {
        final String word;
        final Translation translation;

        public AddTranslation(String word, Translation translation) {
            this.word = word;
            this.translation = translation;
        }

        public String getWord() {
            return word;
        }

        public Translation getTranslation() {
            return translation;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AddTranslation)) return false;

            AddTranslation that = (AddTranslation) o;

            if (!word.equals(that.word)) return false;
            return translation.equals(that.translation);
        }

        @Override
        public int hashCode() {
            int result = word.hashCode();
            result = 31 * result + translation.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "AddTranslation{" +
                    "word='" + word + '\'' +
                    ", translation=" + translation +
                    '}';
        }
    }

    public SagaActor(ActorRef sagaStream) {
        receive(
                ReceiveBuilder.match(
                        String.class, s -> {
                            sagaStream.tell(new Word(self(),s, Optional.empty(), Optional.empty()), ActorRef.noSender());
                        }
                ).match(
                AddEmoji.class, System.out::println
        ).match(
                        AddTranslation.class, System.out::println
                ).
                        matchAny(o -> log().warning("received unknown message")).build()
        );
    }


    static Props props(ActorRef sagaStream) {
        return Props.create(SagaActor.class, () -> new SagaActor(sagaStream));
    }
}
