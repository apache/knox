package org.apache.hadoop.gateway.services.security.impl;

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.net.ntp.TimeStamp;
import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.services.ServiceLifecycleException;
import org.apache.hadoop.gateway.services.security.MasterService;

public class DefaultMasterService implements MasterService {

  private static final String MASTER_PASSPHRASE = "masterpassphrase";
  private static final String MASTER_PERSISTENCE_TAG = "#1.0# " + TimeStamp.getCurrentTime().toDateString();
  private char[] master = null;
  private AESHelper aes = new AESHelper(MASTER_PASSPHRASE);

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.services.security.impl.MasterService#getMasterSecret()
   */
  @Override
  public char[] getMasterSecret() {
    // TODO: check permission call here
    return this.master;
  }
  
  @Override
  public void init(GatewayConfig config, Map<String,String> options) throws ServiceLifecycleException {
    // for testing only
    if (options.containsKey("master")) {
      this.master = options.get("master").toCharArray();
//      return;
    }
    else {
      File masterFile = new File(config.getGatewayHomeDir() + File.separator + "conf" + File.separator + "security", "master");
      if (masterFile.exists()) {
        initializeFromMaster(masterFile);
      }
      else {
        if(options.get( "persist-master").equals("true")) {
          displayWarning();
        }
        promptUser();
        if(options.get( "persist-master").equals("true")) {
          persistMaster(master, masterFile);
        }
      }
    }
  }
  
  private void promptUser() {
    Console c = System.console();
    if (c == null) {
        System.err.println("No console.");
        System.exit(1);
    }

    boolean noMatch;
    do {
        char [] newPassword1 = c.readPassword("Enter master secret: ");
        char [] newPassword2 = c.readPassword("Enter master secret again: ");
        noMatch = ! Arrays.equals(newPassword1, newPassword2);
        if (noMatch) {
            c.format("Passwords don't match. Try again.%n");
        } else {
            this.master = Arrays.copyOf(newPassword1, newPassword1.length);
        }
        Arrays.fill(newPassword1, ' ');
        Arrays.fill(newPassword2, ' ');
    } while (noMatch);
  }

  private void displayWarning() {
    Console c = System.console();
    if (c == null) {
        System.err.println("No console.");
        System.exit(1);
    }
    c.printf("**********************************************************************************************\n");
    c.printf("You have indicated that you would like to persist the master secret for this gateway instance.\n");
    c.printf("Be aware that this is less secure than manually entering the secret on startup.\n");
    c.printf("The persisted file will be encrypted and primarily protected through OS permissions.\n");
    c.printf("**********************************************************************************************\n");
  }

  private void persistMaster(char[] master, File masterFile) {
    AESHelper.PBEAtom atom = encryptMaster(master);
    // TODO: write it to the file - ensuring permissions are set to just this user
    try {
      ArrayList<String> lines = new ArrayList<String>();
      lines.add(MASTER_PERSISTENCE_TAG);
      String iv = new String(Base64.encodeBase64String(atom.iv));
      String cipher = new String(Base64.encodeBase64String(atom.cipher));
      String line = Base64.encodeBase64String((iv + "::" + cipher).getBytes());
      lines.add(line);
      FileUtils.writeLines(masterFile, "UTF8", lines);
      chmod("600", masterFile);
    } catch (IOException e) {
      // TODO log appropriate message that the master secret has not been persisted
      e.printStackTrace();
    }
  }

  private AESHelper.PBEAtom encryptMaster(char[] master) {
    // TODO Auto-generated method stub
    try {
      return aes.encrypt(new String(master));
    } catch (Exception e) {
      // TODO log failed encryption attempt
      // need to ensure that we don't persist now
      e.printStackTrace();
    }
    return null;
  }

  private void initializeFromMaster(File masterFile) {
    try {
      List<String> lines = FileUtils.readLines(masterFile, "UTF8");
      String tag = lines.get(0);
      // TODO: log - if appropriate - at least at finest level
      System.out.println("Loading from persistent master: " + tag);
      System.out.println("Loading from persistent master again: " + tag);
      String line = new String(Base64.decodeBase64(lines.get(1)));
      String[] parts = line.split("::");
      this.master = aes.decrypt(parts[0], parts[1]).toCharArray();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  @Override
  public void start() throws ServiceLifecycleException {
  }

  @Override
  public void stop() throws ServiceLifecycleException {
  }
  
  private void chmod(String args, File file) throws IOException
  {
      // TODO: move to Java 7 NIO support to add windows as well
      // TODO: look into the following for Windows: Runtime.getRuntime().exec("attrib -r myFile");
      if (isUnixEnv()) {
          //args and file should never be null.
          if (args == null || file == null) 
            throw new IOException("nullArg");
          if (!file.exists()) 
            throw new IOException("fileNotFound");

          // " +" regular expression for 1 or more spaces
          final String[] argsString = args.split(" +");
          List<String> cmdList = new ArrayList<String>();
          cmdList.add("/bin/chmod");
          cmdList.addAll(Arrays.asList(argsString));
          cmdList.add(file.getAbsolutePath());
          new ProcessBuilder(cmdList).start();
      }
  }
  
  private boolean isUnixEnv() {
    return (File.separatorChar == '/');
  }
  
  private static class AESHelper {
    
    // TODO: randomize the salt
    private static final byte[] SALT = {
        (byte) 0xA9, (byte) 0x9B, (byte) 0xC8, (byte) 0x32,
        (byte) 0x56, (byte) 0x35, (byte) 0xE3, (byte) 0x03
    };
    private static final int ITERATION_COUNT = 65536;
    private static final int KEY_LENGTH = 128;
    private Cipher ecipher;
    private Cipher dcipher;
    private SecretKey secret;
   
    AESHelper(String passPhrase) {
        SecretKeyFactory factory;
        try {
          factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
          KeySpec spec = new PBEKeySpec(passPhrase.toCharArray(), SALT, ITERATION_COUNT, KEY_LENGTH);
          SecretKey tmp = factory.generateSecret(spec);
          secret = new SecretKeySpec(tmp.getEncoded(), "AES");
   
          ecipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
          ecipher.init(Cipher.ENCRYPT_MODE, secret);
         
          dcipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
          byte[] iv = ecipher.getParameters().getParameterSpec(IvParameterSpec.class).getIV();
          dcipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(iv));
        } catch (NoSuchAlgorithmException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (InvalidKeySpecException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (NoSuchPaddingException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (InvalidKeyException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (InvalidParameterSpecException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
    }
 
    public PBEAtom encrypt(String encrypt) throws Exception {
        byte[] bytes = encrypt.getBytes("UTF8");
        PBEAtom atom = encrypt(bytes);
        //return new Base64().encodeAsString(atom.cipher);
        return atom;
    }
 
    public PBEAtom encrypt(byte[] plain) throws Exception {
      PBEAtom atom = new PBEAtom(ecipher.getParameters().getParameterSpec(IvParameterSpec.class).getIV(), ecipher.doFinal(plain));
      return atom;
    }
 
    public String decrypt(String iv, String cipher) throws Exception {
      byte[] ivbytes = Base64.decodeBase64(iv);
      byte[] bytes = Base64.decodeBase64(cipher);
      byte[] decrypted = decrypt(ivbytes, bytes);
      return new String(decrypted, "UTF8");
    }
 
    public byte[] decrypt(byte[] iv, byte[] encrypt) throws Exception {
      dcipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(iv));
      byte[] more = dcipher.update(encrypt);
      return dcipher.doFinal(more);
    }
    
    private class PBEAtom {
      byte[] iv;
      byte[] cipher;
      PBEAtom(byte[] iv, byte[] cipher) {
        this.iv = iv;
        this.cipher = cipher;
      }
    }
  }
}
