/*
 * J2ME_MPEG: MPEG-1 decoder for J2ME
 *
 * Copyright (c) 2009 David Caabeiro
 *
 */

import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import java.io.*;
import java.util.*;

public class Player extends MIDlet implements CommandListener {
    private VideoRenderer mRenderer;
    private VideoDecoder mDecoder;

    private Thread mRendererTask;

    public Player() {
        Queue queue = new Queue();

        mRenderer = new VideoRenderer(queue);
        mRenderer.addCommand(new Command("Exit", Command.EXIT, 0));
        mRenderer.setCommandListener(this);

        mDecoder = new VideoDecoder(queue, mRenderer);
    }

    public void startApp() {
        Displayable display = new TestCanvas();

        display.addCommand(new Command("Play", Command.SCREEN, 0));
        display.addCommand(new Command("Exit", Command.EXIT, 0));
        display.setCommandListener(this);

        Display.getDisplay(this).setCurrent(display);
    }

    public void pauseApp() {
    }

    public void destroyApp(boolean unconditional) {
    }

    public void commandAction(Command c, Displayable d) {
        if (c.getCommandType() == Command.EXIT) {
            notifyDestroyed();
        }
        else {
            mDecoder.start();

            mRendererTask = new Thread(mRenderer);
            mRendererTask.start();

            Display.getDisplay(this).setCurrent(mRenderer);
        }
    }
}

class TestCanvas extends Canvas {
    protected void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();

        g.setColor(0xffffff);
        g.fillRect(0, 0, w, h);
    }
}


class VideoDecoder extends Thread {
    private InputBitStream mInput = null;
    private Decoder mDecoder	  = null;

    public VideoDecoder(Queue queue, VideoRenderer renderer) {
        try {
            mInput = new InputBitStream("/video.mpg");
            mDecoder = new Decoder(queue, mInput, renderer);
        }
        catch (IOException ignore)
        {}
    }

    public void run() {
        try {
        	mDecoder.start();
        }
        catch (IOException ignore)
        {}
        finally {
        	mInput.close();
        }
    }
}

class VideoRenderer extends Canvas implements Runnable {
	private Queue mQueue;

	private int mWidth;
    private int mHeight;

    private int mX = 0;
    private int mY = 0;

    public VideoRenderer(Queue queue) {
        mQueue = queue;
    }

    void setSize(int width, int height) {
    	mWidth  = width;
    	mHeight = height;

    	mX = (getWidth() - mWidth) / 2;
    	mY = (getHeight() - mHeight) / 2;
    }

    protected void paint(Graphics g) {
        if (mBitmap == null) {
        	g.setColor(0xffffff);
        	g.fillRect(0, 0, getWidth(), getHeight());
        }
        else {
        	// Display fps every 5th frame
        	if (mFrameCount % 5 == 0) {
            	g.setColor(0xffffff);
            	g.fillRect(0, 0, getWidth(), g.getFont().getHeight());

                long fps = (1000 * mFrameCount) / (mEndTime - mStartTime);

        		g.setColor(0x000000);
        		g.drawString("fps: " + fps, 0, 0, Graphics.TOP | Graphics.LEFT);
        	}

            g.drawRGB(mBitmap.mRgb, 0, mWidth, mX, mY, mWidth, mHeight, false);
        }
    }

    private Bitmap mBitmap = null;
    private int mFrameCount = 0;

    private long mStartTime = 0;
    private long mEndTime = 0;

    public void run() {
        mBitmap = null;
        Picture stored = null;

        mStartTime = System.currentTimeMillis();

        while (true) {
            Picture current = (Picture) mQueue.get();

            if (mBitmap == null)
                mBitmap = new Bitmap(mWidth, mHeight);

            /*
             * Display frames in order
             */
    		if (current.mType == Picture.I_TYPE || current.mType == Picture.P_TYPE) {
    			if (mFrameCount == 0) {
    	    		mBitmap.transform(current);
    			}
    			else {
    				if (stored != null) {
    					mBitmap.transform(stored);
    				}
    				stored = current;
    			}
    		}
    		else if(current.mType == Picture.B_TYPE) {
    			mBitmap.transform(current);
    		}

    		++mFrameCount;
            mEndTime = System.currentTimeMillis();

            repaint();
        }
    }
}
