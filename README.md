Nice Camera Example for Android
===============================

During the development of an Android application that used Camera functionalities, my mind 
blowed up when trying to understand how things like CameraPreview, startPreview(), 
addCallbackBuffer() work.

Every single step forward generated a different error... sometimes was my fault... but a 
big contribution came from the **[official documentation](http://developer.android.com/guide/topics/media/camera.html#custom-camera)**.

What's wrong with it? 
---------------------
At least in the November 2013's version, the authors offered a very nice explanation 
of SOME aspects of a Camera app development, but some other information were missing. 
In fact, if you try to follow the tutorial step by step, you'll probably end with an 
app that works good on a 5% of the total amount of Android devices in the world. 
Not so good, really.

So what?
--------

So I decided to make a "custom" guide to this subject, without HTML pages but directly 
with a working Eclipse project. I can't guarantee that it's 100% good (I can't test it 
eveywhere), but surely it's a good starting point and obviously everyone can make it 
better by fixing some mistakes that came from my keyboard.

The project is intended to demonstrate how a basic Camera app should be, and also:
* to describe some key-methods to enable a Camera preview on different screen formats
* to describe how to make it so that it's supported from API 8
* to set the starting point for real-time preview buffers interaction.

How can I help you?
-------------------

If you try this code and discover that it totally crashes on your Android device, *just* fork 
it and try to fix what's going wrong. Please remember some little guidelines:

1. Always write a good documentation before commit
2. Don't add new functionalities that could just make the project more complicated, leave it simple!
3. [Tell me](https://github.com/alessandrofrancesconi/NiceCameraExample/issues) if I made some coding errors and where are they.

Enjoy!
