package dev.dubhe.brace.launcher;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Scanner;

public class Console extends Thread {
    public final Process process;

    public Console(Process process) {
        this.process = process;
        this.setDaemon(true);
    }

    @Override
    public void run() {
        Scanner scanner = new Scanner(System.in).useDelimiter("\\s*\n");
        try (
                OutputStream outputStream = process.getOutputStream();
                OutputStreamWriter streamWriter = new OutputStreamWriter(outputStream);
                BufferedWriter writer = new BufferedWriter(streamWriter)
        ) {
            while (process.isAlive()) {
                String command = scanner.next();
                writer.write(format(command));
                writer.flush();
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    public String format(String command) {
        command = command + '\n';
        return command.startsWith("/") ? command.substring(1) : command;
    }
}
