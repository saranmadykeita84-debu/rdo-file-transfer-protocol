import java.io.*;
import java.net.*;
import java.util.*;

public class Client {
    private static String token = "";  // Jeton d'identification du client

    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);

        // Demander à l'utilisateur de saisir l'IP et le port du serveur
        System.out.println("Veuillez entrer l'adresse IP du serveur : ");
        String serverIP = scanner.nextLine();

        System.out.println("Veuillez entrer le port du serveur : ");
        int serverPort = scanner.nextInt();
        scanner.nextLine();  // Pour consommer la ligne vide après nextInt()

        // Vérifie si le serveur est actif avant d'établir une connexion
        if (!isServerActive(serverIP, serverPort)) {
            System.out.println("Le serveur n'est pas accessible. Veuillez vérifier la disponibilité du serveur.");
            return;  // On arrête l'exécution si le serveur n'est pas accessible
        }

        // Si le serveur est actif, établir la connexion
        Socket socket = new Socket(serverIP, serverPort);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

        // Interaction avec l'utilisateur pour le REGISTER
        System.out.println("Veuillez entrer la commande REGISTER suivie de l'adresse IP, par exemple : REGISTER|127.0.0.1|");
        String registerCommand = scanner.nextLine();
        out.println(registerCommand);  // Envoi de la commande REGISTERED|127.0.0.1 au serveur

        String response = in.readLine();  // Attente de la réponse du serveur
        if (response != null && response.startsWith("REGISTERED")) {
            token = response.split("\\|")[1];
            System.out.println("Réponse du serveur: " + response);
            System.out.println("Jeton reçu: " + token);
        } else {
            System.out.println("Erreur lors de l'enregistrement: " + response);
            socket.close();  // Fermer la connexion en cas d'échec de l'enregistrement
            return;
        }

        // Interaction pour afficher la liste des fichiers
        System.out.println("Veuillez entrer la commande 'LS' avec votre jeton, sous le format 'LS|<token>|' : ");
        String lsCommand = scanner.nextLine();

        // Vérifie que la commande commence bien par "LS|" et contient le token
        if (lsCommand.startsWith("LS|") && lsCommand.endsWith("|")) {
            out.println(lsCommand);  // Envoi de la commande LS|<token> au serveur
            response = in.readLine();  // Attente de la réponse du serveur
            System.out.println("Réponse du serveur: " + response);
        } else {
            System.out.println("Commande invalide. Veuillez entrer la commande sous le format 'LS|<token>|'.");
        }

        // Demander à l'utilisateur s'il veut télécharger un fichier existant dans le serveur avec la
        //Commande "READ" ou bien s'il veut téléverser un fichier existant dans client_files vers le serveur
        // avec la commande "WRITE".
        System.out.println("Entrez la commande 'READ|<token>|<filename>|' pour lire un fichier ou 'WRITE|<token>|<filename>|' pour en envoyer un.");
        String action = scanner.nextLine();

        // Vérifier si la commande commence par "READ" ou "WRITE"
        if (action.startsWith("READ|")) {
            // Extraction des parties de la commande (token et nom du fichier)
            String[] parts = action.split("\\|");
            if (parts.length == 3) {
                String fileName = parts[2];  // Le nom du fichier
                out.println(action);  // Envoi de la commande "READ|<token>|<filename>|" au serveur
                response = in.readLine();
                System.out.println("Réponse du serveur : " + response);

                if (response.startsWith("READ|BEGIN")) {
                    receiveFileFromServer(out, in, fileName);
                } else if (response.startsWith("READ-REDIRECT")) {
                    handleRedirection(response, fileName, token, out, in);  // Appel à la gestion de redirection
                }
            } else {
                System.out.println("Commande READ invalide. Assurez-vous d'utiliser le format 'READ|<token>|<filename>|'");
            }

        } else if (action.startsWith("WRITE|")) {
            // Extraction des parties de la commande (token et nom du fichier)
            String[] parts = action.split("\\|");
            if (parts.length == 3) {
                String fileName = parts[2];  // Le nom du fichier
                File file = new File("client_files/" + fileName);
                if (!file.exists()) {
                    System.out.println("Le fichier n'existe pas.");
                    return;
                }

                // Envoi de la commande WRITE pour commencer l'écriture
                out.println(action);  // Envoi de la commande "WRITE|<token>|<filename>|"
                response = in.readLine();
                System.out.println("Réponse du serveur : " + response);

                // Envoi du fichier par fragments
                sendFileInFragments(file, out, in);
            } else {
                System.out.println("Commande WRITE invalide. Assurez-vous d'utiliser le format 'WRITE|<token>|<filename>|'");
            }
        } else {
            System.out.println("Commande invalide. Veuillez entrer 'READ|<token>|<filename>|' ou 'WRITE|<token>|<filename>|'");
        }

        // Fermeture de la connexion
        socket.close();
    }

    // Méthode pour vérifier si un serveur est actif sur un port spécifique
    private static boolean isServerActive(String ipAddress, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ipAddress, port), 5000);  // Timeout de 5 secondes
            return true;
        } catch (IOException e) {
            return false;  // Le serveur n'est pas accessible
        }
    }

    // Méthode pour gérer les redirections successives
    private static void handleRedirection(String response, String fileName, String token, PrintWriter out, BufferedReader in) throws IOException {
        String[] redirectParts = response.split("\\|");
        String peerServer = redirectParts[1];  // Serveur distant
        String tokenForPeer = redirectParts[2];  // Jeton pour ce serveur

        // Vérifie si le jeton pour le serveur peer est correct
        System.out.println("Jeton pour le serveur peer : " + tokenForPeer);

        // Séparation de l'adresse IP et du port
        String[] addressParts = peerServer.split(":");
        String ip = addressParts[0];
        int port = Integer.parseInt(addressParts[1]);

        // Vérifie si le serveur peer est actif avant de tenter une connexion
        if (!isServerActive(ip, port)) {
            System.out.println("Le serveur peer " + peerServer + " n'est pas accessible.");
            return;  // On arrête la redirection si le serveur peer n'est pas accessible
        }

        // Si le serveur peer est actif, on continue à se connecter
        Socket peerSocket = new Socket(ip, port);
        BufferedReader peerIn = new BufferedReader(new InputStreamReader(peerSocket.getInputStream()));
        PrintWriter peerOut = new PrintWriter(peerSocket.getOutputStream(), true);


        // Demande de lecture du fichier sur le serveur peer avec le jeton
        peerOut.println("READ|" + tokenForPeer + "|" + fileName + "|");
        String peerFinalResponse = peerIn.readLine();
        System.out.println("Réponse du serveur peer : " + peerFinalResponse);

        // Vérifier si le fichier est redirigé à nouveau vers un autre serveur
        if (peerFinalResponse.startsWith("READ-REDIRECT")) {
            // Nouvelle redirection vers un autre serveur
            handleRedirection(peerFinalResponse, fileName, tokenForPeer, peerOut, peerIn);
        } else if (peerFinalResponse.startsWith("READ|BEGIN")) {
            receiveFileFromServer(peerOut, peerIn, fileName);
        }

        peerSocket.close();  // Fermer la connexion avec le serveur peer
    }

    // Méthode pour envoyer un fichier par fragments de 500 caractères
    private static void sendFileInFragments(File file, PrintWriter out, BufferedReader in) throws IOException {
        if (!file.exists()) {
            System.out.println("Le fichier n'existe pas. Vérifiez que le fichier est dans le bon répertoire.");
            System.out.println("Répertoire actuel : " + new File("").getAbsolutePath());  // Affiche le répertoire courant
            return;
        }
        BufferedReader fileReader = new BufferedReader(new FileReader(file));
        StringBuilder fileContent = new StringBuilder();
        String line;
        while ((line = fileReader.readLine()) != null) {
            fileContent.append(line).append("\n");
        }

        String content = fileContent.toString();
        int totalFragments = (int) Math.ceil((double) content.length() / 500);
        System.out.println("Le fichier sera envoyé en " + totalFragments + " fragments.");

        for (int i = 0; i < totalFragments; i++) {
            int offset = i;
            int end = Math.min((offset + 1) * 500, content.length());
            String fragment = content.substring(offset * 500, end);
            boolean isLast = (i == totalFragments - 1);

            // Envoi du fragment
            out.println("FILE|" + file.getName() + "|" + offset + "|" + (isLast ? 1 : 0) + "|" + fragment);
            System.out.println("Fragment envoyé : " + (i + 1) + "/" + totalFragments);

            // Attente de la réponse du client
            String response = in.readLine();  // Lire la ligne de réponse

            if (response != null && response.equals("FILE_RECEIVED")) {
                System.out.println("Fragment reçu avec succès.");
            } else {
                if (response == null) {
                    System.out.println("Aucune réponse reçue. La connexion pourrait être fermée.");
                } else {
                    System.out.println("Réponse incorrecte : " + response);
                }
                break;  // Sortir de la boucle si l'erreur persiste
            }
        }
    }

    // Méthode pour recevoir un fichier du serveur
    private static void receiveFileFromServer(PrintWriter out, BufferedReader in, String fileName) throws IOException {
        // Créer un dossier pour stocker les fichiers téléchargés sur le client
        File directory = new File("client_files");
        if (!directory.exists()) {
            directory.mkdir();
        }

        // Créer un fichier dans le dossier client_files pour recevoir le contenu
        File file = new File(directory, fileName);
        BufferedWriter fileWriter = new BufferedWriter(new FileWriter(file));

        String serverResponse;
        while ((serverResponse = in.readLine()) != null) {
            String[] responseParts = serverResponse.split("\\|");
            if (responseParts[0].equals("FILE")) {
                int offset = Integer.parseInt(responseParts[2]);
                boolean isLast = Integer.parseInt(responseParts[3]) == 1;
                String fragment = responseParts[4];

                // Écrire le fragment dans le fichier
                fileWriter.write(fragment);
                System.out.println("Fragment reçu : " + (offset + 1));

                // Envoyer un accusé de réception du fragment
                out.println("FILE_RECEIVED");
                out.flush(); // Envoyer immédiatement la confirmation

                if (isLast) {
                    fileWriter.close();
                    System.out.println("Fichier complet reçu et sauvegardé.");
                    break; // On a reçu tous les fragments, on arrête
                }
            }
        }
    }
}