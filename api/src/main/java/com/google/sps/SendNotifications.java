package com.google.sps;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.google.api.services.gmail.model.Message;
import com.google.api.core.ApiFuture;
import com.google.api.services.gmail.Gmail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;

import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.*;

import org.apache.commons.codec.binary.Base64;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SendNotifications {
  boolean firebaseInitialized = false;

  public List<EmailCreation> gatherRecipients(HttpServletRequest request, HttpServletResponse response, Firestore db)
      throws IOException {

    String gameID = request.getParameter("gameID");
    String emailType = request.getParameter("emailType");
    List<EmailCreation> emails = new ArrayList<>();

    DocumentReference game = db.collection("games").document(gameID);
    CollectionReference players = db.collection("users");
    try {
      DocumentSnapshot gameDocSnap = game.get().get();
      List<String> playerNames = (List<String>) gameDocSnap.get("players");

      for (String player : playerNames) {
        DocumentSnapshot usersDocSnap = players.document(player).get().get();

        // Retrieves player information
        String playerEmail = usersDocSnap.getString("email");
        String playerName = usersDocSnap.getString("username");

        // Adding player to Email object
        if (emailType.equalsIgnoreCase("start")) {
          emails.add(EmailCreation.startGameEmail(gameID, new User(playerEmail, playerName)));
        } else if (emailType.equalsIgnoreCase("end") || emailType.equalsIgnoreCase("turn")) {
          emails.add(EmailCreation.endGameEmail(gameID, new User(playerEmail, playerName)));
        }

        // Checks if there are any players
        if (emails.isEmpty()) {
          throw new IllegalArgumentException();
        }
      }
    } catch (Exception e) {
      System.out.println("Exception in gatherRecipients" + e);
    }

    return emails;
  }

  public EmailCreation getNextPlayer(HttpServletRequest request, HttpServletResponse response, Firestore db)
      throws IOException {
    String gameID = request.getParameter("gameID");
    DocumentReference game = db.collection("games").document(gameID);
    CollectionReference players = db.collection("users");
    EmailCreation notification = null;

    try {
      DocumentSnapshot gameDocSnap = game.get().get();
      int currentPlayerIndex = (int) ((long) gameDocSnap.get("currentPlayerIndex"));
      String playerName = ((List<String>) gameDocSnap.get("players")).get(currentPlayerIndex);

      DocumentSnapshot usersDocSnap = players.document(playerName).get().get();

      // Retrieves player information
      String playerEmail = usersDocSnap.getString("email");

      notification = EmailCreation.playerTurnEmail(gameID, new User(playerEmail, playerName));
    } catch (Exception e) {
      System.out.println("Exception with DocumentSnapshot" + e);
    }

    if (notification == null) {
      throw new IllegalArgumentException();
    }

    return notification;
  }

  @PostMapping("/api/notify")
  public void sendEmail(HttpServletRequest request, HttpServletResponse response)
      throws IOException, InterruptedException, ExecutionException {

    System.out.println("function called");

    if (!firebaseInitialized) {
      GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
      String projectId = "phoebeliang-step";
      FirebaseOptions options = new FirebaseOptions.Builder().setCredentials(credentials).setProjectId(projectId)
          .build();
      FirebaseApp.initializeApp(options);
      firebaseInitialized = true;
    }

    Firestore db = FirestoreClient.getFirestore();

    System.out.println("Firestore initialized");

    String emailType = request.getParameter("emailType");
    String gameID = request.getParameter("gameID");
    final String FROM = "pictophone.noreply@gmail.com";

    System.out.println(gameID);
    DocumentSnapshot docSnap = db.collection("games").document(gameID).get().get();
    int amtOfPlayers = ((List<String>) docSnap.get("players")).size();
    int currentPlayer = (int) ((long) docSnap.get("currentPlayerIndex"));

    System.out.println(currentPlayer);

    if (emailType.equalsIgnoreCase("start") || emailType.equalsIgnoreCase("end") || (currentPlayer+1) > amtOfPlayers) {
      List<EmailCreation> emails = gatherRecipients(request, response, db);

      System.out.println("gathered recipients..");

      try {
        for (EmailCreation email : emails) {
          createAndSendEmail(email.getEmail(), FROM, email.getSubject(), email.getBody());
        }
      } catch (Exception e) {
        System.out.println("Method Exception: " + e);
        System.err.println(e);
      }
    } else if (emailType.equalsIgnoreCase("turn")) {
      EmailCreation player = getNextPlayer(request, response, db);

      System.out.println("got next player...");

      System.out.println(player.getEmail());

      try {
        createAndSendEmail(player.getEmail(), FROM, player.getSubject(), player.getBody());
      } catch (Exception e) {
        System.out.println("Exception with service: " + e);
      }
    }

  }

  // ***********************HELPER METHODS**********************************

  private static void createAndSendEmail(String to, String from, String subject, String bodyText)
      throws MessagingException {
    Properties props = new Properties();

    Content content = new Content("text/plain", bodyText);
    Email eFrom = new Email(from);
    Email eTo = new Email(to);
    Mail mail = new Mail(eFrom, subject, eTo, content);

    SendGrid sg = new SendGrid(System.getenv("SENDGRID_API_KEY"));
    Request request = new Request();

    try {
      request.setMethod(Method.POST);
      request.setEndpoint("mail/send");
      request.setBody(mail.build());
      Response response = sg.api(request);
      System.out.println(response.getStatusCode());
      System.out.println(response.getBody());
      System.out.println(response.getHeaders());
    } catch(Exception e) {
      System.out.println(e);
    }
  }

  private static Message createMessageWithEmail(MimeMessage emailContent) throws MessagingException, IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    emailContent.writeTo(buffer);
    byte[] bytes = buffer.toByteArray();
    String encodedEmail = Base64.encodeBase64URLSafeString(bytes);
    Message message = new Message();
    message.setRaw(encodedEmail);
    return message;
  }

  private static Message sendMessage(Gmail service, String userId, MimeMessage emailContent)
      throws MessagingException, IOException {
    Message message = createMessageWithEmail(emailContent);
    message = service.users().messages().send(userId, message).execute();

    System.out.println("Message id: " + message.getId());
    System.out.println(message.toPrettyString());
    return message;
  }
}
