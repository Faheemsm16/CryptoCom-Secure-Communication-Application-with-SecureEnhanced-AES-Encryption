package com.example.cryptochat;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;

import java.util.ArrayList;
import java.util.List;

public class ContactsAdapter extends RecyclerView.Adapter<ContactsAdapter.ContactViewHolder> {
    private Context context;
    private List<Contact> contactList;
    private DatabaseReference contactsReference;
    private OnItemClickListener itemClickListener;
    private List<Contact> filteredContactList = new ArrayList<>();
    private List<Contact> originalContactList;
    private OnMessageUpdateListener messageUpdateListener;

    public ContactsAdapter(Context context, List<Contact> contactList, DatabaseReference contactsReference) {
        this.context = context;
        this.contactList = contactList;
        this.contactsReference = contactsReference.child("contacts");
        originalContactList = new ArrayList<>(contactList);

        contactsReference.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot dataSnapshot, String previousChildName) {
                Contact contact = dataSnapshot.getValue(Contact.class);
                contactList.add(contact);
                notifyDataSetChanged();
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot dataSnapshot, String previousChildName) {
                int index = findContactIndexById(dataSnapshot.getKey());
                if (index != -1) {
                    Contact updatedContact = dataSnapshot.getValue(Contact.class);
                    contactList.set(index, updatedContact);
                    notifyItemChanged(index);
                }
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {
                int index = findContactIndexById(dataSnapshot.getKey());
                if (index != -1) {
                    contactList.remove(index);
                    notifyItemRemoved(index);
                }
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot dataSnapshot, String previousChildName) {
                Contact movedContact = dataSnapshot.getValue(Contact.class);
                int oldIndex = findContactIndexById(dataSnapshot.getKey());
                if (oldIndex != -1) {
                    contactList.remove(oldIndex);
                }
                int newIndex = calculateNewIndex(previousChildName);
                if (newIndex != -1) {
                    contactList.add(newIndex, movedContact);
                    notifyItemMoved(oldIndex, newIndex);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("ContactsAdapter", "Database Error: " + databaseError.getMessage());
            }
        });
    }

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_contact, parent, false);
        return new ContactViewHolder(view, itemClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        Contact contact = contactList.get(position);
        holder.contactNameTextView.setText(contact.getName());
    }

    @Override
    public int getItemCount() {
        return contactList.size();
    }

    public void updateContactList(List<Contact> newContactList) {
        contactList.clear();
        contactList.addAll(newContactList);
        notifyDataSetChanged();

        if (newContactList.isEmpty()) {
            contactList.clear();
            contactList.addAll(originalContactList);
            notifyDataSetChanged();
        }
    }

    public interface OnMessageUpdateListener {
        void onMessageUpdate(String contactId);
    }

    public void setOnMessageUpdateListener(OnMessageUpdateListener listener) {
        this.messageUpdateListener = listener;
    }

    private void notifyMessageUpdate(String contactId) {
        if (messageUpdateListener != null) {
            messageUpdateListener.onMessageUpdate(contactId);
        }
    }

    static class ContactViewHolder extends RecyclerView.ViewHolder {
        TextView contactNameTextView;
        OnItemClickListener itemClickListener;

        ContactViewHolder(View itemView, OnItemClickListener listener) {
            super(itemView);
            contactNameTextView = itemView.findViewById(R.id.contactNameTextView);
            itemClickListener = listener;

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (itemClickListener != null) {
                        int position = getAdapterPosition();
                        if (position != RecyclerView.NO_POSITION) {
                            itemClickListener.onItemClick(view, position);
                        }
                    }
                }
            });
        }
    }

    public interface OnItemClickListener {
        void onItemClick(View view, int position);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.itemClickListener = listener;
    }

    public void filterContacts(String searchText) {
        filteredContactList.clear();

        if (TextUtils.isEmpty(searchText)) {
            filteredContactList.addAll(contactList);
        } else {
            for (Contact contact : contactList) {
                if (contact.getName().toLowerCase().contains(searchText.toLowerCase())) {
                    filteredContactList.add(contact);
                }
            }
        }

        notifyDataSetChanged();
    }

    private int findContactIndexById(String contactId) {
        for (int i = 0; i < contactList.size(); i++) {
            if (contactList.get(i).getId().equals(contactId)) {
                return i;
            }
        }
        return -1;
    }

    private int calculateNewIndex(String previousChildName) {
        String numericPart = previousChildName.replace("contact", "");

        try {
            int numericValue = Integer.parseInt(numericPart);
            int newIndex = numericValue + 1;
            return newIndex;
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return -1;
        }
    }
}