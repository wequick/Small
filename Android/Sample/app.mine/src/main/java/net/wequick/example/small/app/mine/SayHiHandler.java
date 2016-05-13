package net.wequick.example.small.app.mine;

import android.content.Context;
import android.widget.Toast;
import net.wequick.small.BundleInterface;

public class SayHiHandler implements BundleInterface {

  @Override public String call(Context ctx, String param) {
    Toast.makeText(ctx, "get input: " + param, Toast.LENGTH_SHORT).show();
    return "Mine bundle's hello";
  }
}
