package com.alsaeedcullivan.ourtrips;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.alsaeedcullivan.ourtrips.adapters.FriendAdapter;
import com.alsaeedcullivan.ourtrips.cloud.AccessDB;
import com.alsaeedcullivan.ourtrips.fragments.CustomDialogFragment;
import com.alsaeedcullivan.ourtrips.models.UserSummary;
import com.alsaeedcullivan.ourtrips.utils.Const;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class FriendActivity extends AppCompatActivity {

    private static final String LIST_KEY = "requests";
    private static final String NAME_KEY = "name";
    private static final String EMAILS_KEY = "emails";

    private FirebaseUser mUser;
    private FriendAdapter mAdapter;
    private ArrayList<UserSummary> mList;
    private HashSet<UserSummary> mRequestSet = new HashSet<>();
    private int selectedIndex;
    private UserSummary selectedFriend;
    private String mName = "";
    private LinearLayout mFriendLayout;
    private ProgressBar mSpinner;
    private TextView mLoadingText;
    private TextView mMessage;
    private HashSet<String> mFriendEmailsSet = new HashSet<>();
    private ArrayList<String> mFriendEmailsList = new ArrayList<>();
    private List<DocumentSnapshot> mEmailDocs = new ArrayList<>();
    private List<DocumentSnapshot> mRequestDocs = new ArrayList<>();
    private String mFriendId;
    private String mFriendEmail;
    private String mFriendName;
    private String mRequestedEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friend);

        // set title and back button
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setTitle(getString(R.string.title_activity_findFriends));

        // get the user
        mUser = FirebaseAuth.getInstance().getCurrentUser();

        // get a reference to the widgets
        ListView listView = findViewById(R.id.request_list);
        mSpinner = findViewById(R.id.friend_spinner);
        mFriendLayout = findViewById(R.id.friend_linear);
        mLoadingText = findViewById(R.id.friend_loading_text);
        mMessage = findViewById(R.id.friend_text);

        // set initial visibility
        mFriendLayout.setVisibility(View.GONE);
        mSpinner.setVisibility(View.VISIBLE);
        mLoadingText.setVisibility(View.VISIBLE);

        // if there is a list in savedInstanceState, there is no need to load from the db
        if (savedInstanceState != null && savedInstanceState.getStringArrayList(LIST_KEY) != null
                && savedInstanceState.getStringArrayList(EMAILS_KEY) != null &&
                savedInstanceState.getString(NAME_KEY) != null) {

            // restore the data
            mList = savedInstanceState.getParcelableArrayList(LIST_KEY);
            mName = savedInstanceState.getString(NAME_KEY);
            mFriendEmailsList = savedInstanceState.getStringArrayList(EMAILS_KEY);
            mFriendEmailsSet.clear();
            if (mFriendEmailsList != null) mFriendEmailsSet.addAll(mFriendEmailsList);
            Log.d(Const.TAG, "onCreate: " + mFriendEmailsSet);

            if (mName == null) mName = "";
            if (mList != null) {
                mRequestSet.clear();
                mRequestSet.addAll(mList);
                // set the adapter to the list view with the saved list
                mAdapter = new FriendAdapter(this, R.layout.activity_friend, new ArrayList<UserSummary>());
                mAdapter.addAll(mList);
                listView.setAdapter(mAdapter);
                listView.setOnItemClickListener(listListener());
                makeListAppear();
                if (mList.size() == 0) mMessage.setText(R.string.no_req);
                else mMessage.setText(R.string.pending_requests);
            }
        } else if (mUser != null) {
            // instantiate the adapter
            mAdapter = new FriendAdapter(this, R.layout.activity_friend, new ArrayList<UserSummary>());
            // set the adapter to the ListView
            listView.setAdapter(mAdapter);
            listView.setOnItemClickListener(listListener());
            // get all the requests
            new GetRequestsTask().execute();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.friend_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.send_request_button) {
            requestDialog();
            return true;
        } else if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mList != null) outState.putParcelableArrayList(LIST_KEY, mList);
        if (mName != null) outState.putString(NAME_KEY, mName);
        if (mFriendEmailsList != null) outState.putStringArrayList(EMAILS_KEY, mFriendEmailsList);
    }

    /**
     * requestDialog()
     * display a dialog that allows the user to send a friend request
     */
    private void requestDialog() {
        CustomDialogFragment.newInstance(CustomDialogFragment.FRIEND_ID)
                .show(getSupportFragmentManager(), CustomDialogFragment.TAG);
    }

    /**
     * sendRequest()
     * called when a user presses "send"
     *
     * @param email the email of the person they are sending the request to
     */
    public void sendRequest(String email) {
        // get the current user
        mUser = FirebaseAuth.getInstance().getCurrentUser();
        if (mUser == null || email == null) return;

        mRequestedEmail = email;

        UserSummary newFriend = new UserSummary();
        newFriend.setEmail(mRequestedEmail);

        if (mRequestedEmail.equals(mUser.getEmail())) {
            Toast t = Toast.makeText(FriendActivity.this, "You cannot send a friend " +
                    "request to yourself", Toast.LENGTH_SHORT);
            t.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0);
            t.show();
            return;
        }
        if (mFriendEmailsSet.contains(mRequestedEmail)) {
            Toast t = Toast.makeText(FriendActivity.this, "You are already friends " +
                    "with this user", Toast.LENGTH_SHORT);
            t.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0);
            t.show();
            return;
        }
        if (mRequestSet.contains(newFriend)) {
            Toast t = Toast.makeText(FriendActivity.this, mRequestedEmail + " has already sent " +
                    "you a friend request!", Toast.LENGTH_SHORT);
            t.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0);
            t.show();
            return;
        }

        // send the request
        new GetUserByEmailTask().execute();
    }

    /**
     * acceptRequest()
     * called when a user accepts a friend request
     *
     * @param friendId the email of the new friend
     */
    public void acceptRequest(String friendId, String friendEmail, String friendName) {
        if (friendId.equals("") || friendEmail.equals("") || friendName.equals("")) return;

        // get the current user
        mUser = FirebaseAuth.getInstance().getCurrentUser();
        if (mUser == null || mName == null) return;

        mFriendId = friendId;
        mFriendEmail = friendEmail;
        mFriendName = friendName;

        Log.d(Const.TAG, "acceptRequest: made it");

        // if there is a user, accept the friend requests
        // db operation on background thread
        new AcceptTask().execute();
    }

    /**
     * declineRequest()
     * declines a friend request
     *
     * @param id the id of the person that sent the request
     */
    public void declineRequest(String id) {
        // get the current user
        mUser = FirebaseAuth.getInstance().getCurrentUser();
        mFriendId = id;
        if (mUser == null || id == null) return;

        // delete the friend request
        new DeleteTask().execute();
    }

    /**
     * removeRequest()
     * removes a request from the list view
     *
     * @param position the position of the request that will be removed
     */
    public void removeRequest(int position) {
        mList.remove(position);
        mAdapter.clear();
        mAdapter.addAll(mList);
        mAdapter.notifyDataSetChanged();
    }

    // returns an OnClickListener for the list view
    private AdapterView.OnItemClickListener listListener() {
        return new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // save the data and display the dialog
                selectedIndex = position;
                selectedFriend = mList.get(position);
                CustomDialogFragment.newInstance(CustomDialogFragment.ACCEPT_ID)
                        .show(getSupportFragmentManager(), CustomDialogFragment.TAG);
            }
        };
    }

    // makes the list view visible
    private void makeListAppear() {
        mSpinner.setVisibility(View.GONE);
        mLoadingText.setVisibility(View.GONE);
        mFriendLayout.setVisibility(View.VISIBLE);
    }

    // GETTERS

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public UserSummary getSelectedFriend() {
        return selectedFriend;
    }


    // ASYNC TASKS

    /**
     * GetRequestsTask
     * handles the db operations that are performed when this activity is first created
     */
    private class GetRequestsTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            if (mUser == null) return null;

            // get the emails of all of this user's friends
            AccessDB.getFriendEmails(mUser.getUid())
                    .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                        @Override
                        public void onComplete(@NonNull Task<QuerySnapshot> task) {
                            QuerySnapshot q = task.getResult();
                            if (q == null) return;
                            // get the documents in the sub collection
                            mEmailDocs = q.getDocuments();
                            new EmailTask().execute();
                        }
                    });
            // get all the friend requests
            AccessDB.getFriendRequests(mUser.getUid())
                    .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                        @Override
                        public void onComplete(@NonNull Task<QuerySnapshot> task) {
                            QuerySnapshot result = task.getResult();
                            if (result != null && result.getDocuments().size() > 0) {
                                // get  the documents and start the async task
                                mRequestDocs = result.getDocuments();
                                new ShowRequestsTask().execute();
                            } else {
                                mMessage.setText(R.string.no_req);
                                makeListAppear();
                            }
                        }
                    });
            // get the name of this user
            Log.d(Const.TAG, "run: name " + Thread.currentThread().getId());
            AccessDB.getUserName(mUser.getUid())
                    .addOnCompleteListener(new OnCompleteListener<String>() {
                        @Override
                        public void onComplete(@NonNull Task<String> task) {
                            Log.d(Const.TAG, "onComplete: name" +  + Thread.currentThread().getId());
                            if (task.isSuccessful()) mName = task.getResult();
                            else mName = "";
                        }
                    });

            return null;
        }
    }

    /**
     * EmailTask
     * adds the friend emails to the email list and email set
     */
    private class EmailTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {

            if (mEmailDocs == null || mEmailDocs.size() == 0) return null;

            // add the emails to the list
            for (DocumentSnapshot doc : mEmailDocs) {
                if (doc.get(Const.USER_EMAIL_KEY) != null)
                    mFriendEmailsList.add((String)doc.get(Const.USER_EMAIL_KEY));
            }
            // add the emails to the set
            mFriendEmailsSet.clear();
            mFriendEmailsSet.addAll(mFriendEmailsList);

            return null;
        }
    }

    /**
     * RequestTask
     * creates list of user summaries for all of the users that have sent this user a friend request
     */
    private class ShowRequestsTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {

            if (mRequestDocs == null || mRequestDocs.size() == 0) return null;

            // instantiate the list of friend requests
            mList = new ArrayList<>();

            // create a user summary for each document
            for (DocumentSnapshot doc : mRequestDocs) {
                UserSummary u = new UserSummary();
                u.setUserId(doc.getId());
                String email = (String)doc.get(Const.USER_EMAIL_KEY);
                if (email != null) u.setEmail(email);
                String name = (String)doc.get(Const.USER_NAME_KEY);
                if (name != null) u.setName(name);
                mList.add(u);
            }

            // add all the user summaries to the request set
            mRequestSet.clear();
            mRequestSet.addAll(mList);

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            // set up the list view
            if (mList != null && mAdapter != null) {
                mAdapter.addAll(mList);
                mAdapter.notifyDataSetChanged();
                if (mList.size() == 0) mMessage.setText(R.string.no_req);
                else mMessage.setText(R.string.pending_requests);
            }
            makeListAppear();

        }
    }

    /**
     * GetUserByEmailTask
     * sends a friend request
     */
    private class GetUserByEmailTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            if (mUser == null || mName == null || mRequestedEmail == null) return null;

            // if there is a user corresponding to this email, check if this user has
            // already sent a request to them
            AccessDB.getUserByEmail(mRequestedEmail)
                    .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                @Override
                public void onComplete(@NonNull Task<QuerySnapshot> task) {
                    QuerySnapshot q = task.getResult();
                    if (q != null && q.getDocuments().size() > 0) {
                        // get the id of the user that the request was sent to
                        mFriendId = q.getDocuments().get(0).getId();
                        // check to see if the current user has already sent them a friend request
                        new CheckAlreadySentTask().execute();
                    } else {
                        Toast t = Toast.makeText(FriendActivity.this, "We could " +
                                "not find a user with the email " + mRequestedEmail, Toast.LENGTH_SHORT);
                        t.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0);
                        t.show();
                    }
                }
            });

            return null;
        }
    }

    /**
     * CheckAlreadySentTask
     * checks to see if the user has already sent a request to this person and that the
     * request is still pending
     */
    private class CheckAlreadySentTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            if (mFriendId == null || mUser == null) return null;

            // check to see if this user has already sent a friend request to the user corresponding
            // to the email that they typed in. If they have not, send the request.
            AccessDB.getFromRequests(mFriendId, mUser.getUid())
                    .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                @Override
                public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                    DocumentSnapshot doc = task.getResult();
                    // if they have not
                    if (doc == null || !doc.exists()) {
                        // send the request
                        new SendRequestTask().execute();
                    } else {
                        Toast t = Toast.makeText(FriendActivity.this, "You have already " +
                                "sent a request to this user and they have not accepted it yet", Toast.LENGTH_SHORT);
                        t.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0);
                        t.show();
                    }
                }
            });

            return null;
        }
    }

    /**
     * SendRequestTask
     * sends a request to the user corresponding to the email that this user typed in
     */
    private class SendRequestTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            if (mFriendId == null || mUser == null || mName == null) return null;

            // add this user's data to a map
            Map<String, Object> data = new HashMap<>();
            data.put(Const.USER_ID_KEY, mUser.getUid());
            data.put(Const.USER_EMAIL_KEY, mUser.getEmail());
            data.put(Const.USER_NAME_KEY, mName);

            // send the request
            AccessDB.sendRequest(mFriendId, mUser.getUid(), data)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    if (task.isSuccessful()) {
                        Toast t = Toast.makeText(FriendActivity.this, "The request was " +
                                "sent successfully!", Toast.LENGTH_SHORT);
                        t.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0);
                        t.show();
                    } else {
                        Toast t = Toast.makeText(FriendActivity.this, "The request " +
                                "could not be sent", Toast.LENGTH_SHORT);
                        t.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0);
                        t.show();
                    }
                }
            });

            return null;
        }
    }

    /**
     * AcceptTask
     * accepts a friend request and updates the db accordingly
     */
    private class AcceptTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            if (mUser == null || mFriendId == null || mFriendName == null || mFriendEmail == null
                    || mName == null) return null;

            // accept the request and update the db accordingly
            AccessDB.acceptRequest(mUser.getUid(), mUser.getEmail(), mName, mFriendId,
                    mFriendEmail, mFriendName);

            return null;
        }
    }

    /**
     * DeleteTask
     * deletes a friend request from the db
     */
    private class DeleteTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            if (mUser == null || mFriendId == null) return null;

            // delete the request
            AccessDB.deleteRequest(mUser.getUid(), mFriendId);

            return null;
        }
    }
}