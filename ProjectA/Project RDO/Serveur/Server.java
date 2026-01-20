import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private static final int DEFAULT_PORT = 1212; // Port par défaut si l'utilisateur n'entre pas un port valide
    private static Map<String, String> filesList = new HashMap<>();  // Liste des fichiers disponibles
    private static Map<String, String> clientTokens = new HashMap<>();  // Liste des jetons clients
    private static Set<String> validPeerTokens = new HashSet<>(); // Liste des jetons valides pour les peers

    public static void main(String[] args) throws IOException {
        // Demander à l'utilisateur l'adresse IP du serveur
        Scanner scanner = new Scanner(System.in);
        System.out.print("Entrez l'adresse IP du serveur (par défaut 'localhost'): ");
        String serverIP = scanner.nextLine();
        if (serverIP.isEmpty()) {
            serverIP = "localhost";  // Valeur par défaut si l'utilisateur ne saisit rien
        }

        // Demander à l'utilisateur le port du serveur
        System.out.print("Entrez le port du serveur (par défaut 2020): ");
        String portInput = scanner.nextLine();
        int serverPort = DEFAULT_PORT;  // Valeur par défaut
        if (!portInput.isEmpty()) {
            try {
                serverPort = Integer.parseInt(portInput);
            } catch (NumberFormatException e) {
                System.out.println("Port invalide, utilisation du port par défaut 2020.");
            }
        }

        loadFilesList();  // Charger la liste des fichiers
        loadPeersList();  // Charger la liste des serveurs peers

        // Créer un ServerSocket en utilisant l'adresse IP et le port spécifiés
       // ServerSocket serverSocket = new ServerSocket(serverPort, 50, InetAddress.getByName(serverIP));
        ServerSocket serverSocket = new ServerSocket(serverPort);
        System.out.println("Serveur démarré sur l'adresse " + serverIP + " et le port " + serverPort);

        // Créer un ExecutorService pour gérer les clients
        ExecutorService executor = Executors.newFixedThreadPool(10);  // Pool avec 10 threads

        // Accepter les connexions des clients
        while (true) {
            Socket clientSocket = serverSocket.accept();
            // Soumettre la connexion du client au pool de threads pour traitement
            executor.submit(new ClientHandler(clientSocket));
        }
    }

    // Charger la liste des fichiers depuis un fichier
    private static void loadFilesList() throws IOException {
        BufferedReader br = new BufferedReader(new FileReader("Serveur/FilesList.txt"));
        String line;
        while ((line = br.readLine()) != null) {
            String[] parts = line.split(" ");
            if (parts.length == 1) {
                filesList.put(parts[0], "localhost");  // Fichier local
            } else {
                filesList.put(parts[0], parts[1]);  // Fichier sur un autre serveur
            }
        }
    }

    // Charger la liste des serveurs peers
    private static void loadPeersList() throws IOException {
        BufferedReader br = new BufferedReader(new FileReader("Serveur/PeersList.txt"));
        String line;
        while ((line = br.readLine()) != null) {
            System.out.println("Peer: " + line);  // Affichage pour le débogage
        }
    }

    // Gérer chaque client connecté
    private static class ClientHandler extends Thread {
        private Socket clientSocket;
        private BufferedReader in;
        private PrintWriter out;
        private PrintWriter peerOut;

        public ClientHandler(Socket socket) throws IOException {
            this.clientSocket = socket;
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new PrintWriter(socket.getOutputStream(), true);
        }

        @Override
        public void run() {
            try {
                String clientMessage;
                while ((clientMessage = in.readLine()) != null) {
                    String[] messageParts = clientMessage.split("\\|");
                    String command = messageParts[0];

                    switch (command) {
                        case "REGISTER":
                            handleRegister(messageParts);
                            break;
                        case "LS":
                            handleLS(messageParts);
                            break;
                        case "WRITE":
                            handleWrite(messageParts);
                            break;
                        case "READ":
                            handleRead(messageParts);
                            break;
                        case "FILE":
                            handleFile(messageParts);
                            break;
                        case "ADD_TOKEN":
                            handleAddToken(messageParts);
                            break;
                        default:
                            out.println("UNKNOWN_COMMAND");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void handleAddToken(String[] messageParts) {
            if (messageParts.length < 2) {
                out.println("ERROR|Invalid ADD_TOKEN command. Token is missing.");
                return;
            }

            String token = messageParts[1];

            // Vérification si le token est déjà dans la liste des tokens valides
            if (!validPeerTokens.contains(token)) {
                validPeerTokens.add(token);
                System.out.println("Token ajouté: " + token);
                out.println("TOKEN_ADDED|" + token);
            } else {
                out.println("ERROR|Token already exists.");
            }
        }

        // Enregistrer le client
        private void handleRegister(String[] messageParts) {
            String clientIP = messageParts[1];
            String token = UUID.randomUUID().toString().substring(0, 20);
            clientTokens.put(token, clientIP);
            out.println("REGISTERED|" + token);
        }

        // Lister les fichiers disponibles
        private void handleLS(String[] messageParts) {
            String token = messageParts[1];
            if (clientTokens.containsKey(token)) {
                // Générer la liste des fichiers disponibles
                StringBuilder fileListResponse = new StringBuilder("LS|");

                // Ajouter chaque fichier dans la liste à la réponse
                for (String fileName : filesList.keySet()) {
                    fileListResponse.append(fileName).append("|");
                }

                // Retirer le dernier caractère "|" et envoyer la réponse
                fileListResponse.deleteCharAt(fileListResponse.length() - 1);
                out.println(fileListResponse.toString());  // Réponse avec les fichiers disponibles
            } else {
                out.println("LS|UNAUTHORIZED");  // Si le token est invalide
            }
        }

        // Gérer une demande d'écriture
        private void handleWrite(String[] messageParts) {
            String token = messageParts[1];
            if (clientTokens.containsKey(token)) {
                out.println("WRITE|BEGIN");
                // Attente des fragments du fichier
                String clientMessage;
                try {
                    while ((clientMessage = in.readLine()) != null) {
                        String[] parts = clientMessage.split("\\|");
                        if (parts[0].equals("FILE")) {
                            handleFile(parts);  // Appel à handleFile pour traiter chaque fragment
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                out.println("WRITE|UNAUTHORIZED");
            }
        }

        // Gérer la réception d'un fichier fragmenté
        private void handleFile(String[] messageParts) throws IOException {
            String fileName = messageParts[1];
            int offset = Integer.parseInt(messageParts[2]);
            boolean isLast = Integer.parseInt(messageParts[3]) == 1;
            String fragment = messageParts[4];

            // Enregistrer le fragment dans le fichier
            File file = new File("Serveur/stockage_fichiers/" + fileName);
            try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                raf.seek(offset * 500);  // Déplacer le curseur à l'offset
                raf.write(fragment.getBytes());  // Écrire le fragment

                // Attente d'une réponse pour chaque fragment reçu
                out.println("FILE_RECEIVED");

                if (isLast) {
                    System.out.println("Fichier reçu et sauvegardé : " + fileName);
                }
            }
        }

        // Gérer la demande de lecture d'un fichier
        private void handleRead(String[] messageParts) {
            String token = messageParts[1];  // Le jeton du client
            String fileName = messageParts[2];  // Le nom du fichier demandé

            if (clientTokens.containsKey(token) || validPeerTokens.contains(token)) {  // Si le jeton est valide
                if (filesList.containsKey(fileName)) {  // Si le fichier est dans la liste
                    if (filesList.get(fileName).equals("localhost")) {  // Vérifier si le fichier est local
                        out.println("READ|BEGIN|" + fileName);  // Début de l'envoi du fichier
                        try {
                            sendFileToClient(fileName);  // Envoi du fichier au client
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {  // Si le fichier est sur un serveur peer (autre serveur.)
                        String peerServer = filesList.get(fileName);  // Serveur qui possède le fichier
                        try {
                            // 1. Contacter le serveur peer pour obtenir son token
                            String peerToken = getPeerToken(peerServer);

                            // 2. Rediriger le client vers ce serveur peer avec son token
                            out.println("READ-REDIRECT|" + peerServer + "|" + peerToken + "|");

                        } catch (IOException e) {
                            out.println("READ|PEER_ERROR");
                            e.printStackTrace();
                        }
                    }
                } else {  // Si le fichier n'existe pas
                    out.println("READ|NOT_FOUND");
                }
            } else {
                out.println("READ|UNAUTHORIZED");  // Si le jeton est invalide
            }
        }

        // Demander le token au serveur peer
        private String getPeerToken(String peerServer) throws IOException {
            // Séparation de l'adresse IP et du port
            String[] addressParts = peerServer.split(":");
            String ip = addressParts[0];
            int port = Integer.parseInt(addressParts[1]);

            // Connexion au serveur peer pour obtenir son token
            // Si le serveur peer est actif, on continue à se connecter
            Socket peerSocket = new Socket(ip, port);
            BufferedReader peerIn = new BufferedReader(new InputStreamReader(peerSocket.getInputStream()));
            PrintWriter peerOut = new PrintWriter(peerSocket.getOutputStream(), true);

            // Demande au serveur peer d'ajouter ce jeton à sa liste des jetons valides
            String peerToken = UUID.randomUUID().toString().substring(0, 20);  // Création du jeton

            peerOut.println("ADD_TOKEN|" + peerToken);  // Envoi de la commande pour ajouter le jeton au serveur peer

            peerSocket.close();
            return peerToken;
        }


        // Envoie le fichier au client par fragments
        private void sendFileToClient(String fileName) throws IOException {
            File file = new File("Serveur/stockage_fichiers/" + fileName);
            BufferedReader reader = new BufferedReader(new FileReader(file));
            StringBuilder fragment = new StringBuilder();
            int fragmentSize = 500;  // Taille maximale de chaque fragment

            int totalFragments = (int) Math.ceil(file.length() / (double) fragmentSize);  // Nombre total de fragments
            for (int i = 0; i < totalFragments; i++) {
                fragment.setLength(0);  // Réinitialiser le StringBuilder pour chaque fragment
                int bytesRead = 0;

                // Lire un fragment de 500 caractères (ou la fin du fichier)
                while (bytesRead < fragmentSize && reader.ready()) {
                    char c = (char) reader.read();
                    fragment.append(c);
                    bytesRead++;
                }

                // Indiquer si c'est le dernier fragment
                boolean isLast = (i == totalFragments - 1);
                out.println("FILE|" + fileName + "|" + i + "|" + (isLast ? 1 : 0) + "|" + fragment.toString());
                System.out.println("Fragment envoyé : " + (i + 1) + "/" + totalFragments);

                // Attente de l'accusé de réception
                String response = in.readLine();
                if ("FILE_RECEIVED".equals(response)) {
                    System.out.println("Fragment " + (i + 1) + " envoyé avec succès.");
                } else {
                    System.out.println("Erreur lors de l'envoi du fragment " + (i + 1));
                    break;
                }
            }
            reader.close();
        }
    }
}
