import os
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

# Try importing moviepy, handle v2 changes gracefully
from moviepy import VideoFileClip, ImageClip, concatenate_videoclips, ColorClip, VideoClip
try:
    # Try importing fade effects if available in v2
    from moviepy.video.fx import FadeIn, FadeOut, MultiplySpeed
    has_vfx = True
except ImportError:
    has_vfx = False

ASSETS_DIR = r"c:\Users\Manasvi Bhargava\SmartCaneApp\video_assets"
OUTPUT_FILE = r"c:\Users\Manasvi Bhargava\SmartCaneApp\video_assets\draft3_cinematic.mp4"

def get_path(filename):
    return os.path.join(ASSETS_DIR, filename)

def create_title_card(title, subtitle, duration=3.0):
    """Creates a very cinematic, elegant title card using the app's brand colors (Slate & Gold)."""
    img = Image.new('RGBA', (1920, 1080), (15, 23, 42, 255)) # Primary Dark Slate (#0F172A)
    draw = ImageDraw.Draw(img)
    
    try:
        font_title = ImageFont.truetype("arialbd.ttf", 90)
        font_sub = ImageFont.truetype("arial.ttf", 55)
    except:
        font_title = ImageFont.load_default()
        font_sub = ImageFont.load_default()
    
    # Measure text
    # Support new and old PIL versions
    if hasattr(draw, 'textbbox'):
        bbox1 = draw.textbbox((0, 0), title, font=font_title)
        w1, h1 = bbox1[2] - bbox1[0], bbox1[3] - bbox1[1]
        bbox2 = draw.textbbox((0, 0), subtitle, font=font_sub)
        w2, h2 = bbox2[2] - bbox2[0], bbox2[3] - bbox2[1]
    else:
        w1, h1 = draw.textsize(title, font=font_title)
        w2, h2 = draw.textsize(subtitle, font=font_sub)
    
    total_h = h1 + h2 + 40
    y1 = (1080 - total_h) / 2
    y2 = y1 + h1 + 40
    
    # Draw gold accent line
    draw.line([(1920/2 - 80, y2 - 20), (1920/2 + 80, y2 - 20)], fill=(234, 179, 8, 255), width=5) # Accent Gold (#EAB308)
    
    # Draw Text
    draw.text(((1920 - w1)/2, y1), title, font=font_title, fill=(255, 255, 255, 255))
    draw.text(((1920 - w2)/2, y2), subtitle, font=font_sub, fill=(148, 163, 184, 255)) # Secondary Text (#94A3B8)
    
    arr = np.array(img)
    clip = ImageClip(arr).with_duration(duration)
    
    if has_vfx:
        try:
            clip = clip.with_effects([FadeIn(0.5), FadeOut(0.5)])
        except:
            pass
    return clip

def ken_burns_effect(img_path, duration=5.0, zoom_start=1.0, zoom_end=1.15):
    """Creates a smooth Ken Burns zoom effect for images."""
    if not os.path.exists(img_path): return None
    img = Image.open(img_path).convert('RGB')
    orig_w, orig_h = img.size
    
    # Crop to 16:9
    target_ratio = 1920 / 1080.0
    img_ratio = orig_w / orig_h
    if img_ratio > target_ratio:
        new_w = int(orig_h * target_ratio)
        img = img.crop(((orig_w - new_w)//2, 0, (orig_w + new_w)//2, orig_h))
    else:
        new_h = int(orig_w / target_ratio)
        img = img.crop((0, (orig_h - new_h)//2, orig_w, (orig_h + new_h)//2))
        
    img = img.resize((1920, 1080), Image.Resampling.LANCZOS)
    base_arr = np.array(img)
    
    def make_frame(t):
        z = zoom_start + (zoom_end - zoom_start) * (t / duration)
        cw, ch = int(1920 / z), int(1080 / z)
        x1, y1 = (1920 - cw)//2, (1080 - ch)//2
        cropped = Image.fromarray(base_arr).crop((x1, y1, x1+cw, y1+ch))
        return np.array(cropped.resize((1920, 1080), Image.Resampling.BILINEAR))
        
    clip = VideoClip(make_frame, duration=duration)
    if has_vfx:
        try: clip = clip.with_effects([FadeIn(0.5), FadeOut(0.5)])
        except: pass
    return clip

def prepare_clip(path, start=None, end=None, speed=1.0):
    if not os.path.exists(path):
        print(f"Missing: {path}")
        return None
    try:
        clip = VideoFileClip(path)
        if start is not None and end is not None:
            clip = clip.subclipped(start, end)
        elif start is not None:
            clip = clip.subclipped(start)
        elif end is not None:
            clip = clip.subclipped(0, end)
            
        if speed != 1.0 and has_vfx:
            try: clip = clip.with_effects([MultiplySpeed(speed)])
            except: pass
            
        clip = clip.resized(height=1080)
        return clip
    except Exception as e:
        print(f"Error processing {path}: {e}")
        return None

def main():
    clips = []
    
    # 0. Cold Open
    clips.append(ColorClip(size=(1920, 1080), color=(0, 0, 0), duration=1))
    clips.append(create_title_card("253 million people live with visual impairment.", "Most navigate the world alone.", 4.0))

    # 1. Product Reveal (Pic 1 with Ken Burns + Title Card overlay concept separated)
    clips.append(create_title_card("Introducing AURA", "A smart assistive system for the visually impaired", 3.0))
    pic1 = ken_burns_effect(get_path("1scene2.jpeg"), duration=4.0)
    if pic1: clips.append(pic1)

    # 2. Hardware Breakdown
    clips.append(create_title_card("Hardware Breakdown", "Built with ESP32 & Ultrasonic/IR Sensors", 2.5))
    vid1 = prepare_clip(get_path("scene3.mp4"), speed=1.5)
    if vid1: clips.append(vid1)

    # 3. App Interface Tour
    clips.append(create_title_card("AURA Android App", "Built for Accessibility • Works Offline", 2.5))
    vid2 = prepare_clip(get_path("scene4.mp4"))
    if vid2: clips.append(vid2)

    # 4. Obstacle Detection
    clips.append(create_title_card("Real-time Obstacle Detection", "Instant Text-to-Speech Alerts", 2.5))
    vid3 = prepare_clip(get_path("scene5A1.mp4"), start=0, end=6)
    if vid3: clips.append(vid3)
    vid5 = prepare_clip(get_path("scene5A3.mp4"), start=3.6, end=13.6)
    if vid5: clips.append(vid5)

    # 5. AI Vision
    clips.append(create_title_card("AI Vision Camera", "Detects: Stairs • Doors • Hazards", 2.5))
    vid6 = prepare_clip(get_path("scene5B2.mp4"), start=2.2, end=6.2)
    if vid6: clips.append(vid6)
    vid9 = prepare_clip(get_path("scene5B1.mp4"), start=5, end=8)
    if vid9: clips.append(vid9)

    # 6. OCR Text Reading
    clips.append(create_title_card("OCR Text Reader", "Point. Scan. Hear.", 2.5))
    vid7_pt1 = prepare_clip(get_path("scene5C.mp4"), start=3, end=14)
    vid7_pt2 = prepare_clip(get_path("scene5C.mp4"), start=27, end=41)
    if vid7_pt1: clips.append(vid7_pt1)
    if vid7_pt2: clips.append(vid7_pt2)

    # 7. Emergency SOS
    clips.append(create_title_card("Emergency SOS", "One tap → SMS with GPS location", 2.5))
    vid8 = prepare_clip(get_path("scene5D1.mp4"), start=3.1)
    if vid8: clips.append(vid8)

    # 8. Impact & End
    clips.append(create_title_card("Low-cost. Portable. Offline.", "Built for everyone, everywhere.", 4.0))
    clips.append(create_title_card("AURA", "Empowering independence through technology.", 5.0))

    if not clips:
        print("No clips found.")
        return

    print("Concatenating clips...")
    final_clip = concatenate_videoclips(clips, method="compose")
    
    print("Writing video output...")
    final_clip.write_videofile(OUTPUT_FILE, fps=24, codec="libx264", audio_codec="aac", audio_fps=44100)
    print("Draft 3 Done!")

if __name__ == "__main__":
    main()
