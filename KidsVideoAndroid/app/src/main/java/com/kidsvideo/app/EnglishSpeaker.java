package com.kidsvideo.app;
import android.content.Context;
import android.speech.tts.TextToSpeech;
import java.util.Locale;
public final class EnglishSpeaker {
    private TextToSpeech tts; private boolean ready=false;
    public EnglishSpeaker(Context context,String localeTag){tts=new TextToSpeech(context.getApplicationContext(),status->{if(status==TextToSpeech.SUCCESS){setLocale(localeTag);ready=true;}});}
    public void setLocale(String tag){if(tts!=null){Locale l=Locale.forLanguageTag(tag);int r=tts.setLanguage(l);if(r==TextToSpeech.LANG_MISSING_DATA||r==TextToSpeech.LANG_NOT_SUPPORTED)tts.setLanguage(Locale.UK);tts.setSpeechRate(AppConfig.isDaughter()?0.92f:1.0f);tts.setPitch(1.0f);}}
    public void speak(String text){if(tts!=null&&ready&&text!=null&&!text.isEmpty())tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"kids-video-"+System.currentTimeMillis());}
    public void shutdown(){if(tts!=null){try{tts.stop();}catch(Exception ignored){} try{tts.shutdown();}catch(Exception ignored){}}}
}
