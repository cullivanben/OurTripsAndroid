package com.alsaeedcullivan.ourtrips.cloud;

import com.alsaeedcullivan.ourtrips.models.Trip;
import com.alsaeedcullivan.ourtrips.models.User;
import com.alsaeedcullivan.ourtrips.utils.Const;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;

import java.util.HashMap;
import java.util.Map;
import android.util.Log;

/**
 * Class to handle interactions with the database
 */
public class AccessDB {


    /**
     * addNewUser()
     * saves a new user to the database
     * @param id the id of the user being added
     * @param data the user info that is being added
     */
    public static Task<Void> addNewUser(String id, Map<String, Object> data) {
        // get a reference to the FireStore database
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // add a new document to the users collection
        return db.collection(Const.USERS_COLLECTION)
                .document(id)
                .set(data);
    }

    /**
     * updateUserProfile()
     * updates the profile of a user
     */
    public static Task<Void> updateUserProfile(String userId, Map<String, Object> data) {
        // get a reference to the FireStore database
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // update the user's profile info
        return db.collection(Const.USERS_COLLECTION)
                .document(userId)
                .update(data);
    }

    /**
     * addUserFriend()
     * adds a new friend to the current user's friends sub-collection
     */
    public static Task<Void> addUserFriend(String userId, String friendId) {
        // get a reference to the FireStore database
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // map to contain the document data
        Map<String, Object> data = new HashMap<>();
        data.put(Const.FRIEND_ID_KEY, friendId);

        // add the friend to the friends sub-collection
        return db.collection(Const.USERS_COLLECTION)
                .document(userId)
                .collection(Const.USER_FRIENDS_COLLECTION)
                .document(friendId)
                .set(data);
    }

    /**
     * addUserTrip()
     * adds a new trip to the current user's trips sub-collection
     */
    public static Task<Void> addUserTrip(String userId, String tripId) {
        // get a reference to the FireStore database
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // map to contain the document data
        Map<String, Object> data = new HashMap<>();
        data.put(Const.TRIP_ID_KEY, tripId);

        // add the trip to the trips sub-collection
        return db.collection(Const.USERS_COLLECTION)
                .document(userId)
                .collection(Const.USER_TRIPS_COLLECTION)
                .document(tripId)
                .set(data);
    }

    /**
     * deleteUser()
     * deletes a user from the database
     */
    public static void deleteUser() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseAuth auth = FirebaseAuth.getInstance();

//        if (auth.getCurrentUser() == null) return;
//        String id = auth.getCurrentUser().getUid();
        String id = "test_user_id";

        String path1 = Const.USERS_COLLECTION+"/"+id+"/"+Const.USER_TRIPS_COLLECTION;
    }

    /**
     * addTrip()
     * adds a new trip to the database
     */
    public static Task<Void> addTrip(String tripId, Map<String, Object> data) {
        // get a reference to the cloud FireStore database
        FirebaseFirestore db = FirebaseFirestore.getInstance();

//        // map to contain the trip data
//        HashMap<String, Object> data = new HashMap<>();
//
//        // add the trip data to the map
//        data.put(Const.TRIP_ID_KEY, trip.getTripId());
//        data.put(Const.TRIP_TITLE_KEY, trip.getTitle());
//        data.put(Const.TRIP_COMMENTS_LIST_KEY, trip.getCommentsList());
//        data.put(Const.TRIP_USERS_LIST_KEY, trip.getUsersList());

        // add the trip to the database
        return db.collection(Const.TRIPS_COLLECTION)
                .document(tripId)
                .set(data);
    }

    public void updateTrip() {

    }

    /**
     * deleteTrip()
     * deletes a trip from the database
     */
    public void deleteTrip() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

    }

}
