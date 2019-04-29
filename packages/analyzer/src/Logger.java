import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Timestamp;

public class Logger {
    private BufferedWriter logWriter;

    public Logger(String fileName) throws IOException {
        logWriter = new BufferedWriter(new FileWriter(fileName, true));
    }

    public synchronized void log(String string) {
        try {
            logWriter.write(new Timestamp(System.currentTimeMillis()) + ": " + string + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void flushLog() {
        try {
            logWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void close() {
        try {
            logWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
