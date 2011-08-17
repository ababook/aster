/*
 * Copyright (C) 2011 0xlab - http://0xlab.org/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authored by Kan-Ru Chen <kanru@0xlab.org>
 */

package org.zeroxlab.aster;

import org.zeroxlab.aster.AsterCommand;
import org.zeroxlab.aster.AsterCommand.CommandListener;

import com.android.chimpchat.core.IChimpImage;

import org.python.core.PyException;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.io.*;
import javax.swing.*;
import javax.script.SimpleBindings;

public class AsterMainPanel extends JPanel {

    static JStatusBar mStatus = new JStatusBar();

    public static void status(String msg) {
        mStatus.setStatus(msg);
    }

    public static void message(String msg) {
        mStatus.message(msg);
    }

    private enum ExecutionState { NORMAL, EXECUTION }

    private AsterWorkspace mWorkspace;
    private JActionList mActionList;

    private CmdConn mCmdConn;

    private MyListener mCmdListener;

    public AsterMainPanel() {
	GridBagLayout gridbag = new GridBagLayout();
	GridBagConstraints c = new GridBagConstraints();
        mCmdListener = new MyListener();

        setLayout(gridbag);
        c.fill = GridBagConstraints.BOTH;

        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 1;
        c.gridheight = 3;
        c.weightx = 0;
        c.weighty = 0;
        mActionList = new JActionList();
        JScrollPane scrollPane = new JScrollPane();
        /*
         * TODO: FIXME:
         * Always show the scroll bar, otherwise when the scroll bar
         * was displayed the scrollPane will resize incorretly.
         */
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.getViewport().setView(mActionList);
        add(scrollPane, c);
        mWorkspace = new AsterWorkspace();
        mActionList.getModel().setRecall(new Touch());
        mActionList.addNewActionListener(new MouseAdapter () {
                public void mouseClicked(MouseEvent e) {
                    AsterCommand cmd = CmdSelector.selectCommand((Component)e.getSource());
                    if (cmd != null) {
                        mActionList.getModel().pushCmd(cmd);
                        mWorkspace.fillCmd(cmd, mCmdListener);
                    }
                }
            });

        c.gridx = 1;
        c.gridy = 0;
        c.gridwidth = 3;
        c.gridheight = 3;
        c.weightx = 0.5;
        c.weighty = 0.5;
        add(mWorkspace, c);

        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = 4;
        c.gridheight = 1;
        c.weightx = 0;
        c.weighty = 0;
        add(mStatus, c);

        setPreferredSize(new Dimension(800, 600));

        mCmdConn = new CmdConn();
        Thread thread = new Thread(mCmdConn);
        thread.start();
    }

    public JMenuBar createMenuBar() {
        JMenuBar menu = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);
        menu.add(fileMenu);
        JMenuItem openItem = new JMenuItem();
        openItem.setAction(new AbstractAction() {
                public void actionPerformed(ActionEvent ev) {
                    try {
                        final JFileChooser fc = new JFileChooser();
                        int returnVal = fc.showOpenDialog(AsterMainPanel.this);
                        if (returnVal == JFileChooser.APPROVE_OPTION) {
                            File file = fc.getSelectedFile();
                            AsterCommand[] cmds = AsterCommandManager.load(file.getAbsolutePath());
                            mActionList.getModel().clear();
                            mActionList.getModel().setRecall(cmds[0]);
                            for (int i = 1; i < cmds.length; i++) {
                                mActionList.getModel().pushCmd(cmds[i]);
                            }
                        }
                    } catch (IOException e) {
                    }
                }
            });
        openItem.setText("Open...");
        openItem.setMnemonic(KeyEvent.VK_O);
        fileMenu.add(openItem);
        JMenuItem saveItem = new JMenuItem();
        saveItem.setAction(new AbstractAction() {
                public void actionPerformed(ActionEvent ev) {
                    try {
                        final JFileChooser fc = new JFileChooser();
                        int returnVal = fc.showSaveDialog(AsterMainPanel.this);
                        if (returnVal == JFileChooser.APPROVE_OPTION) {
                            File file = fc.getSelectedFile();
                            AsterCommandManager.dump(mActionList.getModel().toArray(),
                                                     file.getAbsolutePath());
                        }
                    } catch (IOException e) {
                        System.err.println(e.toString());
                    }
                }
            });
        saveItem.setText("Save...");
        saveItem.setMnemonic(KeyEvent.VK_S);
        fileMenu.add(saveItem);
        return menu;
    }

    class MyListener implements CommandListener {
        public void commandFinished(AsterCommand whichOne) {
            mActionList.getModel().trigger();
            System.out.println("Complete cmd: " + whichOne.getName());
        }
    }

    class CmdConn implements Runnable {

        private boolean mKeepWalking = true;
        private AsterCommand[] mCmds;
        private ExecutionState mState;

        public void finish() {
            mKeepWalking = false;
        }

        public void runCommands(AsterCommand[] cmds) {
            mCmds = cmds;
            switchState(ExecutionState.EXECUTION);
        }

        synchronized void switchState(ExecutionState state) {
            mState = state;
        }

        public void run() {
            mState = ExecutionState.NORMAL;
            AsterCommandManager.connect();

            while(mKeepWalking) {
                if (mState == ExecutionState.NORMAL) {
                    updateScreen();
                } else {
                    System.err.printf("Staring command execution...\n");
                    AsterMainPanel.message("Staring command execution...");
                    try {
                        for (AsterCommand c: mCmds) {
                            System.err.println(c.toScript());
                            c.execute();
                            updateScreen();
                        }
                    } catch (PyException e) {
                        System.out.printf("%s\n", e);
                    }
                    switchState(ExecutionState.NORMAL);
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    System.err.println("Update Screen thread is interrupted");
                    AsterMainPanel.message("Update Screen thread is interrupted");
                    e.printStackTrace();
                }
            }
        }

        private void updateScreen() {
            IChimpImage snapshot = AsterCommandManager.takeSnapshot();
            mWorkspace.setImage(snapshot.createBufferedImage());
            mWorkspace.repaint(mWorkspace.getBounds());
        }
    }
}
