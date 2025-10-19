# Phalanx \- Phase 0 

# Scope

* Send/receive SMS & MMS.
* Organize conversations by thread.
* Reliable notifications. 
* Works with Dual SIMs.

## Core Flows

* View inbox → open thread → read → compose → send.
* Receive message → tap notification → reply.  
* Long-press messages or threads → multi-select → delete/mark as read/mute.

## Features

Messaging

* Send/receive SMS; MMS for images/video up to carrier limit.
* Typing field with character counter.
* Drafts auto-saved per thread.

Inbox & Threads

* Thread list with: contact name/number, last message snippet, timestamp, unread dot/count.  
* Inside thread: bubble layout, timestamps, status (sent/delivered/failed).  
* Quick reply from notification.  
* Search by contact, phone number, and message text.

Contacts

* Contact sync (read-only).  
* $$Unknown numbers show number \+ country code flag. Use a placeholder image for profile pictures.

Notifications

* High-priority for new messages.  
* Group notifications with inline “Mark read” and “Reply”.

Dual SIM

* SIM selector on composer.
* SIM badges in thread list and bubbles.
* Per-SIM defaults (e.g., SIM1 for personal, SIM2 for work).

Message Management

* Mark read/unread (thread and message).  
* $$Delete single message or entire thread (confirm dialog).  
* $$Spam blocking: block number; move to Spam folder.

Settings

* Notifications: sound/vibrate, DND respect.  
* SIM defaults, delivery reports toggle, MMS auto-download on Wi-Fi/cellular.  
* Privacy: hide message content on lock screen.

## UI/UX Guidelines

* Material 3; bottom FAB for new message; top app bar with Search, Overflow.  
* Thread list: large avatars, single-line name, single-line snippet, right-aligned time, unread dot. Swipe affordances.  
* Thread view: bubbles with subtle corner radius; outgoing aligned right, incoming left; timestamps on long-press or inline group headers.  
* Composer bar: text field, attach button, SIM selector chip, send button that indicates SIM color.  
* Empty states with short, helpful copy; no tutorials.

## Data Model

* Thread(id, participants\[\], lastMessageId, unreadCount, mutedUntil, archived, pinned)  
* Message(id, threadId, simSlot, type{in/out}, body, timestamp, status{sent/delivered/failed/received}, attachments\[\])  
* Attachment(id, messageId, mimeType, uri, size)  
* ContactCache(phone → name, avatarUri)  
* BlockedNumber(phone)  
* Settings(perSimDefaults, notifications, mmsPrefs)