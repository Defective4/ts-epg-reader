package io.github.defective4.tv.epgreader;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class Main {

    private static final DateFormat DATE_FMT = new SimpleDateFormat("dd MMM", Locale.ENGLISH);
    private static final DateFormat TIME_FMT = new SimpleDateFormat("HH:mm");

    public static void main(String[] args) throws IOException {
        String file = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().getFile()).getName();
        if (args.length < 2) {
            System.err.println("Usage: java -jar " + file + " [output dir] [input files...]");
            System.exit(3);
            return;
        }
        File outputDir = new File(args[0]);
        if (outputDir.isFile()) {
            System.err.println(outputDir + " already exists!");
            System.exit(1);
            return;
        }

        if (outputDir.isDirectory() && outputDir.list().length > 0) {
            System.err.println(outputDir + " already exists and is not empty!");
            System.exit(1);
            return;
        }

        List<File> inputFiles = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            File f = new File(args[i]);
            if (!f.isFile()) {
                System.err.println("File " + f + " is not a valid file!");
                System.exit(1);
                return;
            }
            inputFiles.add(f);
        }

        if (!outputDir.exists() && !outputDir.mkdirs()) {
            System.err.println("Couldn't create output directory!");
            System.exit(1);
            return;
        }

        System.err.println("Parsing files...");
        Map<String, List<FriendlyEvent>> channels = new LinkedHashMap<>();
        for (File f : inputFiles) {
            System.err.println("Parsing " + f + "...");
            try {
                channels.putAll(TSReader.readTransportStream(f));
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(2);
                return;
            }
        }

        System.err.println("Parsing HTML template...");
        StringBuilder viewerHTML = new StringBuilder();
        try (Reader reader = new InputStreamReader(Main.class.getResourceAsStream("/viewer.html.template"),
                StandardCharsets.UTF_8)) {
            while (true) {
                int read = reader.read();
                if (read == -1) break;
                viewerHTML.append((char) read);
            }
        }

        StringBuilder detailsHTML = new StringBuilder();
        try (Reader reader = new InputStreamReader(Main.class.getResourceAsStream("/details.html.template"),
                StandardCharsets.UTF_8)) {
            while (true) {
                int read = reader.read();
                if (read == -1) break;
                detailsHTML.append((char) read);
            }
        }

        Document doc = Jsoup.parse(viewerHTML.toString());
        System.err.println("Constructing HTML documents...");
        Element channelsEl = doc.getElementById("channels");
        Element programsEl = doc.getElementById("programs");
        for (Map.Entry<String, List<FriendlyEvent>> entry : channels.entrySet()) {
            Element channel = channelsEl.appendElement("th");
            channel.html(entry.getKey());
            Element cardContainer = programsEl.appendElement("td").appendElement("div");
            cardContainer.addClass("program-cards");
            for (FriendlyEvent event : entry.getValue()) {
                String title = event.name();
                if (title.length() > 40) title = title.substring(0, 37) + "...";
                String id = "0x" + Integer.toHexString(event.id());
                Element programCard = cardContainer.appendElement("div");
                programCard.addClass("program-card");
                programCard.attr("start-time", Long.toString(event.startTime()));
                programCard.attr("end-time", Long.toString(event.endTime()));
                programCard.attr("event", entry.getKey() + "/" + id);
                Element titleSpan = programCard.appendElement("span");
                titleSpan.addClass("card-title");
                titleSpan.html(title);
                programCard.appendElement("br");
                Element dateSpan = programCard.appendElement("span");
                dateSpan.addClass("card-time");
                dateSpan.html(DATE_FMT.format(new Date(event.startTime())));
                programCard.append(" ");
                Element timeSpan = programCard.appendElement("span");
                timeSpan.addClass("card-time");
                timeSpan
                        .html(TIME_FMT.format(new Date(event.startTime())) + " - "
                                + TIME_FMT.format(new Date(event.endTime())));

                String details = String
                        .format("""
                                <strong>Title: </strong>%s<br/>
                                <strong>Channel: </strong>%s<br/>
                                <strong>Emission date: </strong>%s<br/>
                                <strong>Duration: </strong>%s (to %s)<br/>
                                <strong>Genre: </strong>%s<br/>
                                <strong>Age rating: </strong>%s<br/>
                                <br/>
                                %s
                                """, event.name(), entry.getKey(),
                                DATE_FMT.format(new Date(event.startTime())) + " "
                                        + TIME_FMT.format(new Date(event.startTime())),
                                Duration.ofMillis(event.endTime() - event.startTime()).toString().substring(2),
                                TIME_FMT.format(new Date(event.endTime())),
                                String.join(", ", event.contentTypes().toArray(new String[0])),
                                event.ageRating() == -1 ? "None" : event.ageRating() + "+", event.description());
                File eventFile = new File(outputDir, "events/" + entry.getKey() + "/" + id + ".html");
                eventFile.getParentFile().mkdirs();
                try (Writer wr = new FileWriter(eventFile)) {
                    wr.write(String.format(detailsHTML.toString(), event.name(), details));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        System.err.println("Writing HTML file...");
        try (Writer writer = new FileWriter(new File(outputDir, "index.html"), StandardCharsets.UTF_8)) {
            writer.write(doc.toString());
        }

        System.err.println("All done!");
    }
}
