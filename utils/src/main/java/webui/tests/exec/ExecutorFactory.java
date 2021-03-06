package webui.tests.exec;


import org.apache.commons.exec.*;
import org.apache.commons.exec.launcher.CommandLauncher;
import org.apache.commons.exec.launcher.CommandLauncherFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * User: guym
 * Date: 3/27/13
 * Time: 3:39 PM
 */
public class ExecutorFactory {

    boolean createWorkaroundExecutor = true;

    public Executor createNew(){
        return createWorkaroundExecutor ? getWorkaroundExecutor() : getDefaultExecutor();
    }

    private Executor getDefaultExecutor(){
        return new DefaultExecutor();
    }

    private Executor getWorkaroundExecutor(){
         return new WorkaroundExecutor();
    }


    // guy - could not find a way to resolve the bootstrap issue, had to use a workaround
    //http://stackoverflow.com/questions/15618570/java-process-execution-gets-stuck-on-script-that-does-not-return-properly
    public static class WorkaroundExecutor implements Executor{

            /** taking care of output and error stream */
            private ExecuteStreamHandler streamHandler;

            /** the working directory of the process */
            private File workingDirectory;

            /** monitoring of long running processes */
            private ExecuteWatchdog watchdog;

            /** the exit values considered to be successful */
            private int[] exitValues;

            /** launches the command in a new process */
            private final CommandLauncher launcher;

            /** optional cleanup of started processes */
            private ProcessDestroyer processDestroyer;

            /** worker thread for asynchronous execution */
            private Thread executorThread;

            /**
             * Default constructor creating a default <code>PumpStreamHandler</code>
             * and sets the working directory of the subprocess to the current
             * working directory.
             *
             * The <code>PumpStreamHandler</code> pumps the output of the subprocess
             * into our <code>System.out</code> and <code>System.err</code> to avoid
             * into our <code>System.out</code> and <code>System.err</code> to avoid
             * a blocked or deadlocked subprocess (see{@link java.lang.Process Process}).
             */
            public WorkaroundExecutor() {
                this.streamHandler = new PumpStreamHandler();
                this.launcher = CommandLauncherFactory.createVMLauncher();
                this.exitValues = new int[0];
                this.workingDirectory = new File(".");
            }

            /**
             * @see org.apache.commons.exec.Executor#getStreamHandler()
             */
            public ExecuteStreamHandler getStreamHandler() {
                return streamHandler;
            }

            /**
             * @see org.apache.commons.exec.Executor#setStreamHandler(org.apache.commons.exec.ExecuteStreamHandler)
             */
            public void setStreamHandler(ExecuteStreamHandler streamHandler) {
                this.streamHandler = streamHandler;
            }

            /**
             * @see org.apache.commons.exec.Executor#getWatchdog()
             */
            public ExecuteWatchdog getWatchdog() {
                return watchdog;
            }

            /**
             * @see org.apache.commons.exec.Executor#setWatchdog(org.apache.commons.exec.ExecuteWatchdog)
             */
            public void setWatchdog(ExecuteWatchdog watchDog) {
                this.watchdog = watchDog;
            }

            /**
             * @see org.apache.commons.exec.Executor#getProcessDestroyer()
             */
            public ProcessDestroyer getProcessDestroyer() {
              return this.processDestroyer;
            }

            /**
             * @see org.apache.commons.exec.Executor#setProcessDestroyer(ProcessDestroyer)
             */
            public void setProcessDestroyer(ProcessDestroyer processDestroyer) {
              this.processDestroyer = processDestroyer;
            }

            /**
             * @see org.apache.commons.exec.Executor#getWorkingDirectory()
             */
            public File getWorkingDirectory() {
                return workingDirectory;
            }

            /**
             * @see org.apache.commons.exec.Executor#setWorkingDirectory(java.io.File)
             */
            public void setWorkingDirectory(File dir) {
                this.workingDirectory = dir;
            }

            /**
             * @see org.apache.commons.exec.Executor#execute(CommandLine)
             */
            public int execute(final CommandLine command) throws ExecuteException,
                    IOException {
                return execute(command, (Map ) null);
            }

            /**
             * @see org.apache.commons.exec.Executor#execute(CommandLine, java.util.Map)
             */
            public int execute(final CommandLine command, Map environment)
                    throws ExecuteException, IOException {

                if (workingDirectory != null && !workingDirectory.exists()) {
                    throw new IOException(workingDirectory + " doesn't exist.");
                }

                return executeInternal(command, environment, workingDirectory, streamHandler);

            }

            /**
             * @see org.apache.commons.exec.Executor#execute(CommandLine,
             *      org.apache.commons.exec.ExecuteResultHandler)
             */
            public void execute(final CommandLine command, ExecuteResultHandler handler)
                    throws ExecuteException, IOException {
                execute(command, null, handler);
            }

            /**
             * @see org.apache.commons.exec.Executor#execute(CommandLine,
             *      java.util.Map, org.apache.commons.exec.ExecuteResultHandler)
             */
            public void execute(final CommandLine command, final Map environment,
                    final ExecuteResultHandler handler) throws ExecuteException, IOException {

                if (workingDirectory != null && !workingDirectory.exists()) {
                    throw new IOException(workingDirectory + " doesn't exist.");
                }

                executorThread = new Thread() {
                    public void run() {
                        int exitValue = Executor.INVALID_EXITVALUE;
                        try {
                            exitValue = executeInternal(command, environment, workingDirectory, streamHandler);
                            handler.onProcessComplete(exitValue);
                        } catch (ExecuteException e) {
                            handler.onProcessFailed(e);
                        } catch(Exception e) {
                            handler.onProcessFailed(new ExecuteException("Execution failed", exitValue, e));
                        }
                    }
                };

                getExecutorThread().start();
            }

            /** @see org.apache.commons.exec.Executor#setExitValue(int) */
            public void setExitValue(final int value) {
                this.setExitValues(new int[] {value});
            }


            /** @see org.apache.commons.exec.Executor#setExitValues(int[]) */
            public void setExitValues(final int[] values) {
                this.exitValues = (values == null ? null : (int[]) values.clone());
            }

            /** @see org.apache.commons.exec.Executor#isFailure(int) */
            public boolean isFailure(final int exitValue) {

                if(this.exitValues == null) {
                    return false;
                }
                else if(this.exitValues.length == 0) {
                    return this.launcher.isFailure(exitValue);
                }
                else {
                    for(int i=0; i<this.exitValues.length; i++) {
                        if(this.exitValues[i] == exitValue) {
                            return false;
                        }
                    }
                }
                return true;
            }

            /**
             * Creates a process that runs a command.
             *
             * @param command
             *            the command to run
             * @param env
             *            the environment for the command
             * @param dir
             *            the working directory for the command
             * @return the process started
             * @throws IOException
             *             forwarded from the particular launcher used
             */
            protected Process launch(final CommandLine command, final Map env,
                    final File dir) throws IOException {

                if (this.launcher == null) {
                    throw new IllegalStateException("CommandLauncher can not be null");
                }

                if (dir != null && !dir.exists()) {
                    throw new IOException(dir + " doesn't exist.");
                }
                return this.launcher.exec(command, env, dir);
            }

            /**
             * Get the worker thread being used for asynchronous execution.
             *
             * @return the worker thread
             */
            protected Thread getExecutorThread() {
                return executorThread;
            }

            /**
             * Close the streams belonging to the given Process. In the
             * original implementation all exceptions were dropped which
             * is probably not a good thing. On the other hand the signature
             * allows throwing an IOException so the current implementation
             * might be quite okay.
             *
             * @param process the <CODE>Process</CODE>.
             * @throws IOException closing one of the three streams failed
             */
            private void closeStreams(final Process process) throws IOException {

//                IOException caught = null;
//
//                try {
//                    process.getInputStream().close();
//                }
//                catch(IOException e) {
//                    caught = e;
//                }
//
//                try {
//                    process.getOutputStream().close();
//                }
//                catch(IOException e) {
//                    caught = e;
//                }
//
//                try {
//                    process.getErrorStream().close();
//                }
//                catch(IOException e) {
//                    caught = e;
//                }
//
//                if(caught != null) {
//                    throw caught;
//                }
            }

            /**
             * Execute an internal process.
             *
             * @param command the command to execute
             * @param environment the execution enviroment
             * @param dir the working directory
             * @param streams process the streams (in, out, err) of the process
             * @return the exit code of the process
             * @throws IOException executing the process failed
             */
            private int executeInternal(final CommandLine command, final Map environment,
                    final File dir, final ExecuteStreamHandler streams) throws IOException {

                final Process process = this.launch(command, environment, dir);

                try {
                    streams.setProcessInputStream(process.getOutputStream());
                    streams.setProcessOutputStream(process.getInputStream());
                    streams.setProcessErrorStream(process.getErrorStream());
                } catch (IOException e) {
                    process.destroy();
                    throw e;
                }

                streams.start();

                try {

                    // add the process to the list of those to destroy if the VM exits
                    if(this.getProcessDestroyer() != null) {
                      this.getProcessDestroyer().add(process);
                    }

                    // associate the watchdog with the newly created process
                    if (watchdog != null) {
                        watchdog.start(process);
                    }

                    int exitValue = Executor.INVALID_EXITVALUE;

                    try {
                        exitValue = process.waitFor();
                    } catch (InterruptedException e) {
                        process.destroy();
                    }
                    finally {
                        // see http://bugs.sun.com/view_bug.do?bug_id=6420270
                        // see https://issues.apache.org/jira/browse/EXEC-46
                        // Process.waitFor should clear interrupt status when throwing InterruptedException
                        // but we have to do that manually
                        Thread.interrupted();
                    }

                    if (watchdog != null) {
                        watchdog.stop();
                    }

//                    streams.stop();
//                    closeStreams(process);

                    if (watchdog != null) {
                        try {
                            watchdog.checkException();
                        } catch (IOException e) {
                            throw e;
                        } catch (Exception e) {
                            throw new IOException(e.getMessage());
                        }
                    }

                    if(this.isFailure(exitValue)) {
                        throw new ExecuteException("Process exited with an error: " + exitValue, exitValue);
                    }

                    return exitValue;
                } finally {
                    // remove the process to the list of those to destroy if the VM exits
                    if(this.getProcessDestroyer() != null) {
                      this.getProcessDestroyer().remove(process);
                    }
                }
            }
    }

}
