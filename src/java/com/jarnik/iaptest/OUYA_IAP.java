package com.jarnik.iaptest;

import org.haxe.nme.HaxeObject;
import java.util.Arrays;
import android.util.Log;
import java.util.List;
import java.util.ArrayList;
import android.os.Bundle;
import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import tv.ouya.console.api.Purchasable;
import tv.ouya.console.api.Product;
import tv.ouya.console.api.OuyaFacade;
import tv.ouya.console.api.CancelIgnoringOuyaResponseListener;
import tv.ouya.console.api.OuyaResponseListener;
import tv.ouya.console.api.OuyaErrorCodes;
import tv.ouya.console.api.OuyaEncryptionHelper;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.util.*;

public class OUYA_IAP
{
	/*
	public static void requestProduct(final HaxeObject callback)
	{
		GameActivity.getInstance().runOnUiThread
		(
			new Runnable()
			{ 
				public void run() 
				{
					callback.call("onPurchase", new Object[] {"junk"});
				}
			}
		);
		//callback.call("onPurchase", new Object[] {"junk"});
	}*/
	
	/**
     * The outstanding purchase request UUIDs.
     */

    private static final Map<String, Product> mOutstandingPurchaseRequests = new HashMap<String, Product>();
	
	public static List<Purchasable> PRODUCT_IDENTIFIER_LIST;
	public static HaxeObject mCallback;
	private static List<Product> mProductList; 
	private static OuyaFacade mOuyaFacade;
	private static PublicKey mPublicKey;
	
	public static void init(final HaxeObject callback, OuyaFacade ouyaFacade, String APPLICATION_KEY_64 )
	{
		mOuyaFacade = ouyaFacade;
		mCallback = callback;
		
		Log.d("IAP", "Java here, running init!");
		
		Log.d("IAP", "got APPLICATION_KEY_64 "+APPLICATION_KEY_64);
		
		byte[] APPLICATION_KEY = Base64.decode( APPLICATION_KEY_64, Base64.NO_WRAP );
		Log.d("IAP", "received APP KEY bytes "+APPLICATION_KEY[0]+" "+APPLICATION_KEY[1]);
		
        // Create a PublicKey object from the key data downloaded from the developer portal.
        try {
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(APPLICATION_KEY);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            mPublicKey = keyFactory.generatePublic(keySpec);
        } catch (Exception e) {
            Log.e("IAP", "Unable to create encryption key", e);
        }
	}
	
	public static void requestProductList(String[] products)
	{
		Log.d("IAP", " will request "+products[0]+" etc " );
		
		PRODUCT_IDENTIFIER_LIST = new ArrayList<Purchasable>();
		for ( String s : products )
			PRODUCT_IDENTIFIER_LIST.add( new Purchasable( s ) );
		
		Log.d("IAP", "========== created product list of "+PRODUCT_IDENTIFIER_LIST.size() );
		
		mOuyaFacade.requestProductList(PRODUCT_IDENTIFIER_LIST, new CancelIgnoringOuyaResponseListener<ArrayList<Product>>() {
            @Override
            public void onSuccess(final ArrayList<Product> products) {
                mProductList = products;
				for(Product p : products) {
                    Log.d("IAP", p.getName() + " costs " + p.getPriceInCents());
                }
				Log.d("IAP", "========== requestProductList SUCCESS "+products.size() );
				//callback.call("onPurchase", new Object[] {"junk"});
				mCallback.call("onProductListReceived", new Object[] { products } );
            }

            @Override
            public void onFailure(int errorCode, String errorMessage, Bundle optionalData) {
                // Your app probably wants to do something more sophisticated than popping a Toast. This is
                // here to tell you that your app needs to handle this case: if your app doesn't display
                // something, the user won't know of the failure.
                //Toast.makeText(IapSampleActivity.this, "Could not fetch product information (error " + errorCode + ": " + errorMessage + ")", Toast.LENGTH_LONG).show();
				Log.d("IAP", "========== requestProductList FAIL " );
				mCallback.call("onProductListFailed",  new Object[] { errorCode + ": " + errorMessage } );
            }
        }); 
		
		Log.d("IAP", "========== requested product list " );
		//callback.call("onPurchase", new Object[] {"junk"});
	}
	
	public static void requestPurchase( String productName ) 
		throws GeneralSecurityException, UnsupportedEncodingException, JSONException {
		
		Product product = null;
		for ( Product p: mProductList )
			if ( p.getIdentifier() == productName ) {
				product = p;
				break;
			}
		if ( product == null ) {
			Log.w("IAP", "Requested product ID "+productName+" not found in requested products!" );
            return;
		}
		
		Log.d("IAP", "========== requesting purchase of "+product.getName() );
			
        SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");

        // This is an ID that allows you to associate a successful purchase with
        // it's original request. The server does nothing with this string except
        // pass it back to you, so it only needs to be unique within this instance
        // of your app to allow you to pair responses with requests.
        String uniqueId = Long.toHexString(sr.nextLong());

        JSONObject purchaseRequest = new JSONObject();
        purchaseRequest.put("uuid", uniqueId);
        purchaseRequest.put("identifier", product.getIdentifier());
        String purchaseRequestJson = purchaseRequest.toString();
		Log.w("IAP", "HEYYA requesting "+product.getIdentifier()+" uuid "+uniqueId);

        byte[] keyBytes = new byte[16];
        sr.nextBytes(keyBytes);
        SecretKey key = new SecretKeySpec(keyBytes, "AES");

        byte[] ivBytes = new byte[16];
        sr.nextBytes(ivBytes);
        IvParameterSpec iv = new IvParameterSpec(ivBytes);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "BC");
        cipher.init(Cipher.ENCRYPT_MODE, key, iv);
        byte[] payload = cipher.doFinal(purchaseRequestJson.getBytes("UTF-8"));

        cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding", "BC");
        cipher.init(Cipher.ENCRYPT_MODE, mPublicKey);
        byte[] encryptedKey = cipher.doFinal(keyBytes);

        Purchasable purchasable =
                new Purchasable(
                        product.getIdentifier(),
                        Base64.encodeToString(encryptedKey, Base64.NO_WRAP),
                        Base64.encodeToString(ivBytes, Base64.NO_WRAP),
                        Base64.encodeToString(payload, Base64.NO_WRAP) );

        synchronized (mOutstandingPurchaseRequests) {
            mOutstandingPurchaseRequests.put(uniqueId, product);
        }
		Log.w("IAP", "HEYYA ouyaFacade.requestPurchase");
        mOuyaFacade.requestPurchase(purchasable, new PurchaseListener(product));
	}
	
    /**
     * The callback for when the user attempts to purchase something. We're not worried about
     * the user cancelling the purchase so we extend CancelIgnoringOuyaResponseListener, if
     * you want to handle cancelations differently you should extend OuyaResponseListener and
     * implement an onCancel method.
     *
     * @see tv.ouya.console.api.CancelIgnoringOuyaResponseListener
     * @see tv.ouya.console.api.OuyaResponseListener#onCancel()
     */
    public static class PurchaseListener implements OuyaResponseListener<String> {
        /**
         * The ID of the product the user is trying to purchase. This is used in the
         * onFailure method to start a re-purchase if they user wishes to do so.
         */

        private Product mProduct;

        /**
         * Constructor. Store the ID of the product being purchased.
         */

        PurchaseListener(final Product product) {
            mProduct = product;
        }

        /**
         * Handle a successful purchase.
         *
         * @param result The response from the server.
         */
        @Override
        public void onSuccess(String result) {
            Product product;
            String id;
			Log.w("IAP", "HEYYA PurchaseListener.onSuccess");
            try {
                OuyaEncryptionHelper helper = new OuyaEncryptionHelper();

                JSONObject response = new JSONObject(result);
                if(response.has("key") && response.has("iv")) {
                    id = helper.decryptPurchaseResponse(response, mPublicKey);
                    Product storedProduct;
                    synchronized (mOutstandingPurchaseRequests) {
                        storedProduct = mOutstandingPurchaseRequests.remove(id);
                    }
                    if(storedProduct == null || !storedProduct.getIdentifier().equals(mProduct.getIdentifier())) {
                        onFailure(OuyaErrorCodes.THROW_DURING_ON_SUCCESS, "Purchased product is not the same as purchase request product", Bundle.EMPTY);
                        return;
                    }
                } else {
                    product = new Product(new JSONObject(result));
                    if(!mProduct.getIdentifier().equals(product.getIdentifier())) {
                        onFailure(OuyaErrorCodes.THROW_DURING_ON_SUCCESS, "Purchased product is not the same as purchase request product", Bundle.EMPTY);
                        return;
                    }
                }
            } catch (ParseException e) {
                onFailure(OuyaErrorCodes.THROW_DURING_ON_SUCCESS, e.getMessage(), Bundle.EMPTY);
            } catch (JSONException e) {
                if(e.getMessage().contains("ENCRYPTED")) {
                    // This is a hack for some testing code which will be removed
                    // before the consumer release
                    try {
                        product = new Product(new JSONObject(result));
                        if(!mProduct.getIdentifier().equals(product.getIdentifier())) {
                            onFailure(OuyaErrorCodes.THROW_DURING_ON_SUCCESS, "Purchased product is not the same as purchase request product", Bundle.EMPTY);
                            return;
                        }
                    } catch (JSONException jse) {
                        onFailure(OuyaErrorCodes.THROW_DURING_ON_SUCCESS, e.getMessage(), Bundle.EMPTY);
                        return;
                    }
                } else {
                    onFailure(OuyaErrorCodes.THROW_DURING_ON_SUCCESS, e.getMessage(), Bundle.EMPTY);
                    return;
                }
            } catch (IOException e) {
                onFailure(OuyaErrorCodes.THROW_DURING_ON_SUCCESS, e.getMessage(), Bundle.EMPTY);
                return;
            } catch (GeneralSecurityException e) {
                onFailure(OuyaErrorCodes.THROW_DURING_ON_SUCCESS, e.getMessage(), Bundle.EMPTY);
                return;
            }

			//TODO request recipes
            //requestReceipts();
        }

        @Override
        public void onFailure(int errorCode, String errorMessage, Bundle optionalData) {
			Log.w("IAP", "onPurchaseFailed "+errorCode+": "+errorMessage);
			mCallback.call("onPurchaseFailed", new Object[] { errorCode+": "+errorMessage } );
        }

        /*
         * Handling the user canceling
         */
        @Override
        public void onCancel() {
			Log.w("IAP", "HEYYA PurchaseListener.onCancel");
			mCallback.call("onPurchaseCancelled", new Object[] {} );
            //showError("User cancelled purchase");
        }
    }
	
}

