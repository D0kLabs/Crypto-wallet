package com.d0klabs.cryptowalt;

import android.os.Build;
import android.support.annotation.RequiresApi;

import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;

public class CryptoBase {
    public static char[] passwd = null;
    static long modVal = 1;
    static Long num;
    static final String AB = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    static String[][] S;
    static String[] P;




    public static String getRandom(){ //only m*256! Set random!
        SecureRandom secureRandom = new SecureRandom();
        char[] random= new char[256];
        String s;
        for (int i=0; i<256;++i){
            int free = secureRandom.nextInt();
            s = Character.toString((char) free);
            random[i] = s.charAt(0);
        }
        String passphrase = new String (random);
        return passphrase;
    }


    // to convert hexadecimal to binary.
    //AbhayBhat code. Java version of  Blowfish, free and cutted.
    @RequiresApi(api = Build.VERSION_CODES.O)
    static String hexToBin(String plainText)
    {
        String binary = "";
        String binary4B;
        int n = plainText.length();
        for (int i = 0; i < n; i++) {

            num = Long.parseUnsignedLong(plainText.charAt(i) + "", 16);
            binary4B = Long.toBinaryString(num);

            // each value in hexadecimal is 4 bits in binary.
            binary4B = "0000" + binary4B;

            binary4B = binary4B.substring(binary4B.length() - 4);
            binary += binary4B;
        }
        return binary;
    }
    // convert from binary to hexadecimal.
    @RequiresApi(api = Build.VERSION_CODES.O)
    static String binToHex(String plainText)
    {

        long num = Long.parseUnsignedLong(plainText, 2);
        String hexa = Long.toHexString(num);
        while (hexa.length() < (plainText.length() / 4))

            // maintain output length same length
            // as input by appending leading zeroes.
            hexa = "0" + hexa;

        return hexa;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private static String xor(String a, String b)
    {
        a = hexToBin(a);
        b = hexToBin(b);
        String ans = "";
        for (int i = 0; i < a.length(); i++)
            ans += (char)(((a.charAt(i) - '0')
                    ^ (b.charAt(i) - '0'))
                    + '0');
        ans = binToHex(ans);
        return ans;
    }

    // addition modulo 2^32 of two hexadecimal strings.
    @RequiresApi(api = Build.VERSION_CODES.O)
    private static String add(String a, String b) {
        long modVal = 1;
        modVal <<= 32;
        long t_a = Long.parseUnsignedLong(a, 16);
        long t_b = Long.parseUnsignedLong(b, 16);
        t_a = (t_a + t_b) % modVal;
        a = Long.toHexString(t_a);
        while (a.length() < b.length())
            a = "0" + a;
        return a;
    }

    // function F explained above.
    @RequiresApi(api = Build.VERSION_CODES.O)
    private static String F(String text) {
        String a[] = new String[4];
        String ans = "";
        for (int i = 0; i < 8; i += 2) {
            int col = Integer.parseInt(text.substring(i, i + 2), 16);
            a[i / 2] = S[i / 2][col];
        }
        ans = add(a[0], a[1]);
        ans = xor(ans, a[2]);
        ans = add(ans, a[3]);
        return ans;
    }

    // generate subkeys.
    @RequiresApi(api = Build.VERSION_CODES.O)
    private static void pKeyGenerate(String key)
    {
        int j = 0;
        for (int i = 0; i < P.length; i++) {

            // xor-ing 32-bit parts of the key
            // with initial subkeys.
            P[i] = xor(P[i], key.substring(j, j + 8));

         /*   System.out.println("subkey "
                    + (i + 1) + ": "
                    + P[i]); */
            j = (j + 8) % key.length();
        }
    }

    // round function
    @RequiresApi(api = Build.VERSION_CODES.O)
    static String round(String text, String key) {
        String left = text.substring(0, 8);
        String right = text.substring(8, 16);
        left = xor(left, key);
        String f = F(left);
        right = xor(f, right);
        return right + left;
    }

    // encryption
    @RequiresApi(api = Build.VERSION_CODES.O)
    private static String encrypt(String plainText)
    {
        for (int i = 0; i < 16; i++)
            plainText = round(plainText, P[i]);

        // postprocessing
        String right = plainText.substring(0, 8);
        String left = plainText.substring(8, 16);
        right = xor(right, P[16]);
        left = xor(left, P[17]);
        return left + right;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private static String decrypt (String encryptedData){
        for (int i = 17; i > 1; i--)
            encryptedData = round(encryptedData, P[i]);
        String right = encryptedData.substring(0, 8);
        String left = encryptedData.substring(8, 16);
        right = xor(right, P[1]);
        left = xor(left, P[0]);
        return left + right;
    }
    // This code is contributed by AbhayBhat. Thanks about that!

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static String getEncrypted(String data) {
        String key = "aabb09182736ccdd";  //hex too
        String cipherText = "";
        //(<<1 is equivalent to multiply by 2)
        for (int i = 0; i < 32; i++) {
            modVal = modVal << 1;
        }
        pKeyGenerate(key);
        try {
            cipherText = modRetyping(data);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return cipherText;
    }

    public static boolean findByte (byte[] a, byte[] b){
        boolean bool= false;
        for (int i = 0; i <a.length ; i++) {
            if (a[i]==b[0]){
                for (int ii=1; ii<b.length; ii++){
                    if (a[i+ii] != b[ii]) break;
                    else if (ii == b.length-1) bool = true;
                }
            }
        }
        return bool;
    }
    //TODO: Rewrite this @shit!
}
