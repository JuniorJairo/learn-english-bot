package bot.cmd;

import bot.App;
import bot.Constants;
import bot.util.Languages;
import com.deepl.api.TextResult;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;

import java.util.*;

public class TranslateCommand extends BotCommand {

    public TranslateCommand() {
        super("translate", "Translate your text in to a different language!");

        // Add target argument along with options
        CommandArgument targetArg = new CommandArgument(
            OptionType.STRING,
            "target",
            "The language you wish to translate to",
            true, true);

        try {
            Languages.languages = App.translator.getTargetLanguages();
            Languages.languages.forEach(language -> targetArg.addOption(language.getName()));
        } catch (Exception e) {
            e.printStackTrace();
        }

        getArguments().put(targetArg.getName(), targetArg);
        getArguments().put("text", new CommandArgument(
                OptionType.STRING,
                "text",
                "The text to translate",
            true, false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        final Map<String, String> args = new HashMap<>();

        // If there is an empty argument, let the user know.
        this.getArguments().forEach((name, option) -> {
            OptionMapping om = event.getOption(name);

            if (om == null) {
                event.reply("Missing arguments. Please try again!")
                    .setEphemeral(true)
                    .queue();
                return;
            }
            args.put(option.getName(), om.getAsString());
        });

        String targetLang = Languages.getCodeFromDisplay(args.get("target"));
        String text = args.get("text");

        // Check if length exceeds maximum translation length.
        if (text.length() > Constants.MAX_TRANSLATION_LENGTH) {
            event.reply("You can not translate a text that's longer than "
                    + Constants.MAX_TRANSLATION_LENGTH + " characters! Try something shorter.")
                .setEphemeral(true)
                .queue();
            return;
        }

        event.deferReply().queue();

        // Finally, attempt to make the translation.
        try {
            TextResult result = App.translator.translateText(text, null, targetLang);
            Member member = Objects.requireNonNull(event.getMember());
            String displayName = member.getNickname() != null
                ? member.getNickname() : event.getUser().getName();

            event.getHook().editOriginal(
                Languages.getEmojiFromCode(targetLang)
                + " **" + displayName + "**: " + result.getText()).queue();
        } catch (Exception e) {
            event.getHook().setEphemeral(true);
            event.getHook().editOriginal("I was not able to translate that :frowning2:")
                .queue();
            e.printStackTrace();
        }

    }

}
