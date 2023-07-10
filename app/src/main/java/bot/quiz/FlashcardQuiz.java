package bot.quiz;

import bot.service.UserService;
import lombok.Getter;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.util.*;

/**
 * Represents flashcard quiz, mostly used by user journals.
 */
@Getter
public class FlashcardQuiz extends Quiz<MessageEmbed> {

    private final UserService userService;

    private final PrivateChannel channel;

    private String lastMessageId;

    private static final Map<String, FlashcardQuiz> quizes = new HashMap<>();

    protected FlashcardQuiz(User user,
                            UserService userService,
                            PrivateChannel channel,
                            Map<Integer, Question<MessageEmbed>> questions) {
        this.user = user;
        this.userService = userService;
        this.channel = channel;
        this.questions = questions;

        quizes.put(user.getId(), this);
    }

    @Override
    public void showQuestion(int number) {
        Button revealButton = Button.success("flashcard-reveal", "Reveal");

        show(getQuestions().get(number).getQuestion(), List.of(revealButton));
    }

    @Override
    public void showAnswer(int number) {
        List<Button> buttons = new ArrayList<>();

        for (int i = 0; i <= 5; i++) {
            int hex = 0x20E3 + i;
            Button button = Button.primary(
                "flashcard-answer-" + i,
                Emoji.fromUnicode("U+" + Integer.toHexString(hex)));

            buttons.add(button);
        }
        show(getQuestions().get(number).getAnswer(), buttons);
    }

    private void show(MessageEmbed messageEmbed, List<Button> buttons) {
        if (messageEmbed == null) return;

        if (this.getLastMessageId() != null)
            channel.deleteMessageById(this.getLastMessageId()).queue();

        channel.sendMessage("")
            .setEmbeds(messageEmbed)
            .setActionRow(buttons)
            .queue(message -> lastMessageId = message.getId());
    }

    @Override
    public void start() {
        showQuestion(1);
    }

    public static Optional<FlashcardQuiz> getInstance(String discordId) {
        return Optional.ofNullable(quizes.get(discordId));
    }
}
