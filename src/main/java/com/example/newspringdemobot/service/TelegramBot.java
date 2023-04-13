package com.example.newspringdemobot.service;

import com.example.newspringdemobot.config.BotConfig;
import com.example.newspringdemobot.model.Ads;
import com.example.newspringdemobot.model.AdsRepository;
import com.example.newspringdemobot.model.User;
import com.example.newspringdemobot.model.UserRepository;
import com.vdurmont.emoji.EmojiParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdsRepository adsRepository;

    final BotConfig config;

    static final String HELP_TEXT = """
            This bot is created to demonstrate Spring capabilities.

            You can execute commands from the main menu on the left or by typing a command:

            Type /start to see a welcome message\s

            Type /mydata to see data stored about yourself

            Type /help to see this massage again
                        
            Type /deletedata to delete your data from storage (the deletion is irrevocable)
                        
            Type /settings to set your preferences""";

    static final String YES_BUTTON = "YES_BUTTON";
    static final String NO_BUTTON = "NO_BUTTON";

    static final String ERROR_TEXT = "Error occurred: ";

    public TelegramBot(BotConfig config) {
        this.config = config;
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "get a welcome message"));
        listOfCommands.add(new BotCommand("/mydata", "get your data stored"));
        listOfCommands.add(new BotCommand("/deletedata", "delete your data"));
        listOfCommands.add(new BotCommand("/help", "info how to use this bot"));
        listOfCommands.add(new BotCommand("/settings", "set your preferences"));
        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("Error setting bot's command list: " + e.getMessage());
        }
    }


    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            if (messageText.contains("/send") && config.getOwnerId() == chatId) {
                var textToSend = EmojiParser.parseToUnicode(messageText.substring(messageText.indexOf(" ")));
                var users = userRepository.findAll();
                for (User user : users) {
                    sendMessage(user.getChatId(), textToSend);
                }
            } else {
                switch (messageText) {
                    case "/start" -> {
                        registerUser(update.getMessage());
                        startCommandReceived(chatId, update.getMessage().getChat().getUserName());
                    }
                    case "/help" -> sendMessage(chatId, HELP_TEXT);
                    case "/mydata" -> myDataCommandReceived(update.getMessage());
                    case "/deletedata" -> deleteDataCommandReceived(update.getMessage());
                    case "/register" -> register(chatId);
                    default -> sendMessage(chatId, "Sorry,command was not recognized");
                }
            }
        } else if (update.hasCallbackQuery()) {
            CallbackQuery callbackQuery = update.getCallbackQuery();
            long messageId = callbackQuery.getMessage().getMessageId();
            long chatId = callbackQuery.getMessage().getChatId();

            String callbackData = callbackQuery.getData();
            EditMessageText message = new EditMessageText();

            if (callbackData.equals(YES_BUTTON)) {
                String text = "You pressed YES button";
                executeEditMessageText((int) messageId, chatId, message, text);

            } else if (callbackData.equals(NO_BUTTON)) {
                String text = "You pressed NO button";
                executeEditMessageText((int) messageId, chatId, message, text);
            }


        }
    }

    private void executeEditMessageText(int messageId, long chatId, EditMessageText message, String text) {
        message.setChatId(chatId);
        message.setText(text);
        message.setMessageId(messageId);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error(ERROR_TEXT + e.getMessage());
        }
    }

    private void register(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Do you really want to register?");

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInLine = new ArrayList<>();
        List<InlineKeyboardButton> rowInLIne = new ArrayList<>();

        var yesButton = new InlineKeyboardButton();
        yesButton.setText("Yes");
        yesButton.setCallbackData(YES_BUTTON);
        rowInLIne.add(yesButton);

        var noButton = new InlineKeyboardButton();
        noButton.setText("NO");
        noButton.setCallbackData(NO_BUTTON);
        rowInLIne.add(noButton);

        rowsInLine.add(rowInLIne);
        markupInline.setKeyboard(rowsInLine);
        message.setReplyMarkup(markupInline);

        executor(message);
    }

    private void deleteDataCommandReceived(Message message) {
        Optional<User> userOptional = userRepository.findById(message.getChatId());
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            userRepository.delete(user);
            sendMessage(message.getChatId(), "Your data was deleted");
            log.info("User delete data " + user.getUserName());
        } else {
            sendMessage(message.getChatId(), "Your data is not in the storage");
        }
    }

    private void myDataCommandReceived(Message message) {
        Optional<User> userOptional = userRepository.findById(message.getChatId());
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            sendMessage(message.getChatId(), user.toString());
            log.info("User get data " + user.getUserName());
        } else {
            sendMessage(message.getChatId(), "Your data is not in the storage");
        }
    }

    private void registerUser(Message message) {
        if (userRepository.findById(message.getChatId()).isEmpty()) {
            long chatId = message.getChatId();
            Chat chat = message.getChat();

            User user = new User();
            user.setChatId(chatId);
            user.setUserName(chat.getUserName());
            user.setFirstName(chat.getFirstName());
            user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));

            userRepository.save(user);
            log.info("User saves " + user.getUserName());
        }
    }

    private void startCommandReceived(long chatId, String name) {

        String answer = EmojiParser.parseToUnicode("Hi, " + name + ", nice to meet you!" + ":blush:");
        log.info("Replied to user " + name);
        sendMessage(chatId, answer);

    }

    private void sendMessage(long chatId, String textToSend) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(textToSend);

        //verticalKeyboard(message, new String[]{"Test button", "Back"});

        executor(message);
    }

    private void executor(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error(ERROR_TEXT + e.getMessage());
        }
    }

    private static void verticalKeyboard(SendMessage message, String[] args) {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();

        for (String arg : args) {
            row.add(arg);
            keyboardRows.add(row);
            row = new KeyboardRow();
        }

        keyboardMarkup.setKeyboard(keyboardRows);
        message.setReplyMarkup(keyboardMarkup);
    }
    @Scheduled(cron = "${cron.scheduler}")
    private void sendAds(){
        var ads = adsRepository.findAll();
        var users = userRepository.findAll();
        for (Ads ad : ads){
            for(User user: users){
                sendMessage(user.getChatId(),ad.getAd());
            }
        }
    }
}
