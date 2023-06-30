package bot;

import bot.cmd.*;
import bot.listener.ReadyListener;
import com.deepl.api.Translator;
import com.mongodb.client.MongoClients;
import dev.morphia.Datastore;
import dev.morphia.Morphia;
import io.github.cdimascio.dotenv.Dotenv;
import it.sauronsoftware.cron4j.Scheduler;
import lombok.extern.log4j.Log4j;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.apache.logging.log4j.Level;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@SpringBootApplication
@Component
@Log4j2
public class App implements ApplicationRunner {

    public static final Scheduler scheduler = new Scheduler();

    private static Datastore datastore;

    private static Dotenv dotenv;
    public static Translator translator;


    public static List<EventListener> listeners = new ArrayList<>();
    public static List<BotCommand> commands = new ArrayList<>();

    static {
        // Listeners
        listeners.add(new ReadyListener());

        commands.add(new PingVoiceChatCommand());
        commands.add(new DefineCommand());
        commands.add(new TOTDCommand());
    }

    public static final List<String> languages = new ArrayList<>();

    public void launch() {
        try {
            dotenv = Dotenv.configure().directory("..").load();
        } catch (Exception e) {
            dotenv = null;
        }

        datastore = acquireDatastore();

        // Schedule daily translation reset task.
        scheduler.schedule(Constants.CRON_DAILY_MORNING, () -> {
            // Clear all translation usages.
            TranslateCommand.getUsages().clear();
            App.log.log(Level.INFO, "Cleaned member daily translation usage!");
        });

        scheduler.start();
        // Add DeepL supported languages and construct translate command.
        try {
            // Slash commands
            App.translator = new Translator(App.getenv("KEY_DEEPL"));
            TranslateCommand cmd = new TranslateCommand();

            commands.add(cmd);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            String envVar = App.getenv("PROD_BUILD");
            int isProdBuild;

            if (envVar != null) {
                isProdBuild = Integer.parseInt(envVar);
            } else {
               isProdBuild = 0;
            }

            String botToken = isProdBuild == 1 ? "BOT_TOKEN_PROD" : "BOT_TOKEN_DEV";
            JDABuilder jdaBuilder = JDABuilder.createDefault(App.getenv(botToken));

            jdaBuilder.enableIntents(GatewayIntent.GUILD_VOICE_STATES);

            // Add event listeners
            listeners.forEach(jdaBuilder::addEventListeners);
            commands.forEach(jdaBuilder::addEventListeners);

            JDA jda = jdaBuilder.build();
            jda.awaitReady();

            Optional<Guild> guildOptional = jda.getGuilds().stream().findFirst();
            if (guildOptional.isEmpty()) {
                App.log.error("Unable to find a guild! You need to invite the bot to one" +
                    "before executing this program. Attempting to exit...");
                System.exit(1);
            }
            Guild guild = guildOptional.get();

            // Register slash commands
            Collection<CommandData> data = commands.stream()
                .map(command -> {
                    SlashCommandData scd = Commands.slash(command.getName(), command.getDescription());

                    command.getArguments().forEach((name, arg) -> scd.addOption(
                        arg.getType(), name, arg.getDescription(),
                        arg.isRequired(), arg.shouldAutocomplete()
                    ));
                    return scd;
                })
                .collect(Collectors.toList());
            guild.updateCommands().addCommands(data).queue();

            // TOTD and WOTD stuff
            TOTDHandler.getTotd().createFallbackTopic();
            scheduler.schedule(Constants.CRON_DAILY_MIDDLE, () -> {
                TOTDHandler.getTotd().executeCron(guild);
                WOTDHandler.getWotd().executeCron(guild);
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Datastore acquireDatastore() {
        final Datastore datastore = Morphia.createDatastore(
            MongoClients.create(getenv("MORPHIA_URI")),
            getenv("MORPHIA_DATABASE"));

        datastore.getMapper().mapPackage("bot.entity");
        datastore.ensureIndexes();

        return datastore;
    }

    public static Datastore getDatastore() {
        return datastore;
    }

    // TODO (@christolis): There must be a better way
    // to get environment variables more easily.
    public static String getenv(String key) {
        // Prioritize .env first
        String value = null;

        if (dotenv != null)
            value = dotenv.get(key);

        return value != null ? value : System.getenv(key);
    }
    public static void main(String[] args) {
        SpringApplication.run(App.class, args).close();
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        launch();
    }
}
