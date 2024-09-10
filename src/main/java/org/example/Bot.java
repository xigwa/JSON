package org.example;

import com.google.gson.Gson;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Bot extends TelegramLongPollingBot {
    private String botUsername;
    private String botToken;
    private static final String FILE_PATH = "json-db.txt";
    private final Gson gson = new Gson();
    private final Map<Long, String> deletionRequests = new HashMap<>();

    public Bot() {
        loadBotCredentials("key.txt");
    }

    private void loadBotCredentials(String filePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            this.botUsername = reader.readLine();
            this.botToken = reader.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            Message message = update.getMessage();
            long chatID = message.getChatId();
            String getText = message.getText();

            if (getText.equals("/start")) {
                sendText(chatID, "Привіт! Я ваш персональний бот. Ось що я можу зробити:\n" +
                        "\n" +
                        "/start: Вітаю! Надішліть ваші дані у форматі: Ім'я, Вік, Email, Дата народження, Номер телефону.\n" +
                        "/view Ім'я: Показує інформацію про особу з вказаним ім'ям.\n" +
                        "/save Ім'я, Вік, Email, Дата народження, Номер телефону: Зберігає ваші дані. Перевіряє, чи ім'я унікальне.\n" +
                        "/all: Відображає список усіх збережених імен.\n" +
                        "/delete Ім'я: Видаляє запис з вказаним ім'ям. Підтвердження видалення необхідне – відповідайте 'так' для підтвердження або 'ні' для скасування.\n" +
                        "Якщо виникнуть питання або потребуєте допомоги пишіть розробнику @Xigwa ");
            } else if (getText.startsWith("/view ")) {
                String name = getText.substring(6).trim();
                viewPersonInfo(chatID, name);
            } else if (getText.startsWith("/save ")) {
                String data = getText.substring(6).trim();
                try {
                    Person person = parseUserData(data);
                    if (isNameUnique(person.getName())) {
                        savePersonToFile(person);
                        sendText(chatID, "Ваші дані збережено.");
                    } else {
                        sendText(chatID, "Людина з таким іменем вже існує.");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    sendText(chatID, "Не вдалося обробити ваші дані. Переконайтеся, що він має правильний формат.");
                }
            } else if (getText.equals("/all")) listAllNames(chatID);
            else if (getText.startsWith("/delete ")) {
                String name = getText.substring(8).trim();
                promptDeleteConfirmation(chatID, name);
            } else if (deletionRequests.containsKey(chatID)) {
                if (getText.equalsIgnoreCase("yes"))
                    handleDeletionConfirmation(chatID, true);
                else if (getText.equalsIgnoreCase("no"))
                    handleDeletionConfirmation(chatID, false);
            } else
                sendText(chatID, "Невідома команда. Використовуйте /view, /save, /all, або /delete.");
        }
    }

    private void promptDeleteConfirmation(Long chatID, String name) {
        sendText(chatID, "Ви дійсно хочете видалити " + name + "? Дайте відповідь «так» для підтвердження або «ні» для скасування.");
        deletionRequests.put(chatID, name);
    }

    private void handleDeletionConfirmation(Long chatID, boolean confirmed) {
        String nameToDelete = deletionRequests.get(chatID);
        if (confirmed) {
            try {
                deletePersonFromFile(nameToDelete);
                sendText(chatID, "Запис для " + nameToDelete + " було видалено.");
            } catch (IOException e) {
                e.printStackTrace();
                sendText(chatID, "Не вдалося видалити запис.");
            }
        } else
            sendText(chatID, "Видалення " + nameToDelete + " було скасовано.");
        deletionRequests.remove(chatID);
    }

    private void deletePersonFromFile(String nameToDelete) throws IOException {
        File originalFile = new File(FILE_PATH);
        File tempFile = new File(FILE_PATH + ".tmp");
        try (BufferedReader reader = new BufferedReader(new FileReader(originalFile));
             BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Person person = gson.fromJson(line, Person.class);
                if (person != null && !person.getName().equalsIgnoreCase(nameToDelete)) {
                    writer.write(line);
                    writer.newLine();
                }
            }
        }

        if (!originalFile.delete())
            throw new IOException("Не вдалося видалити оригінальний файл");

        if (!tempFile.renameTo(originalFile))
            throw new IOException("Не вдалося перейменувати тимчасовий файл на оригінальний");
    }

    private void viewPersonInfo(Long chatID, String name) {
        try (BufferedReader reader = new BufferedReader(new FileReader(FILE_PATH))) {
            String line;
            boolean found = false;
            while ((line = reader.readLine()) != null) {
                Person person = gson.fromJson(line, Person.class);
                if (person != null && person.getName().equalsIgnoreCase(name)) {
                    String info = String.format("Name: %s\nAge: %d\nBirth Date: %s\nEmail: %s\nPhone Number: %s",
                            person.getName(), person.getAge(), person.getBirthDate(), person.getEmail(), person.getPhoneNumber());
                    sendText(chatID, info);
                    found = true;
                    break;
                }
            }
            if (!found)
                sendText(chatID, "Особа не знайдена.");
        } catch (IOException e) {
            e.printStackTrace();
            sendText(chatID, "Під час отримання даних сталася помилка.");
        }
    }

    private boolean isNameUnique(String name) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(FILE_PATH))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Person person = gson.fromJson(line, Person.class);
                if (person != null && person.getName().equalsIgnoreCase(name))
                    return false;
            }
        }
        return true;
    }

    private void listAllNames(Long chatID) {
        try (BufferedReader reader = new BufferedReader(new FileReader(FILE_PATH))) {
            String line;
            StringBuilder namesList = new StringBuilder("Список імен:\n");
            while ((line = reader.readLine()) != null) {
                Person person = gson.fromJson(line, Person.class);
                if (person != null) {
                    namesList.append(person.getName()).append("\n");
                }
            }
            sendText(chatID, namesList.toString());
        } catch (IOException e) {
            e.printStackTrace();
            sendText(chatID, "Під час отримання списку імен сталася помилка.");
        }
    }

    private Person parseUserData(String data) throws Exception {
        String[] parts = data.split(",\\s*");
        if (parts.length != 5)
            throw new Exception("Недійсний формат даних");
        Person person = new Person();
        person.setName(parts[0]);
        person.setAge(Integer.parseInt(parts[1]));
        person.setEmail(parts[2]);
        person.setBirthDate(convertDateFormat(parts[3]));
        person.setPhoneNumber(parts[4]);
        return person;
    }

    private String convertDateFormat(String dateStr) throws ParseException {
        SimpleDateFormat inputFormat = new SimpleDateFormat("dd.MM.yyyy");
        SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date date = inputFormat.parse(dateStr);
        return outputFormat.format(date);
    }

    private void savePersonToFile(Person person) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_PATH, true))) {
            String jsonString = gson.toJson(person);
            writer.write(jsonString);
            writer.newLine();
        }
    }

    public void sendText(Long who, String what) {
        SendMessage sm = SendMessage.builder()
                .chatId(who.toString())
                .text(what)
                .build();
        try {
            execute(sm);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }
}
