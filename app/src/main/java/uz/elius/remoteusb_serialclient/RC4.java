package uz.elius.remoteusb_serialclient;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

class RC4 {
    private int x; /*!< permutation index */
    private int y; /*!< permutation index */
    private byte[] sbox = new byte[256];

    private void swap_sbox(int ind1, int ind2) {
        byte temp = sbox[ind1];
        sbox[ind1] = sbox[ind2];
        sbox[ind2] = temp;
    }

    RC4(final byte[] nonce) throws NoSuchAlgorithmException {
        x = 0;
        y = 0;

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        byte[] key = digest.digest(nonce);

        for (int i = 0; i < 256; i++)
            sbox[i] = (byte) i;

        int j = 0;
        for (int i = 0; i < 256; i++) {
            j = (j + sbox[i] + key[i % key.length]) & 0xFF;
            swap_sbox(i, j);
        }
    }

    byte[] crypt(final byte[] plaintext) {
        int rand;
        byte[] ciphertext = new byte[plaintext.length];

        for (int counter = 0; counter < plaintext.length; counter++) {
            x = (x + 1) & 0xFF;
            y = (y + sbox[x]) & 0xFF;
            swap_sbox(x, y);

            rand = sbox[(sbox[x] + sbox[y]) & 0xFF];
            ciphertext[counter] = (byte) (plaintext[counter] ^ rand);
        }
        return ciphertext;
    }
}
