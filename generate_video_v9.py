import os
import sys
import numpy as np
import cv2
from PIL import Image, ImageDraw, ImageFont

from moviepy import VideoFileClip, ImageClip, concatenate_videoclips, ColorClip, VideoClip, CompositeVideoClip, AudioFileClip, CompositeAudioClip

ASSETS_DIR = r"c:\Users\Manasvi Bhargava\SmartCaneApp\video_assets"
OUTPUT_FILE = r"c:\Users\Manasvi Bhargava\SmartCaneApp\video_assets\draft9_masterpiece_nobgvoice.mp4"

def get_path(filename):
    return os.path.join(ASSETS_DIR, filename)

BGM_CLIP = None
try:
    BGM_CLIP = AudioFileClip(get_path("paulyudin-presentation-music-164836.mp3"))
except Exception as e:
    print("Warning: Could not load BGM", e)

global_time = 0.0

def get_bgm(start_time, duration, volume):
    if BGM_CLIP is None: return None
    track_len = BGM_CLIP.duration
    start = start_time % track_len
    end = start + duration
    
    if end <= track_len:
        segment = BGM_CLIP.subclipped(start, end)
    else:
        from moviepy import concatenate_audioclips
        part1 = BGM_CLIP.subclipped(start, track_len)
        part2 = BGM_CLIP.subclipped(0, end - track_len)
        segment = concatenate_audioclips([part1, part2])
        
    try:
        from moviepy.audio.fx import MultiplyVolume
        segment = segment.with_effects([MultiplyVolume(volume)])
    except:
        segment = segment.with_volume_multiplied(volume)
    return segment

def add_bgm_to_clip(clip, is_video=False):
    global global_time
    duration = clip.duration
    # Back to standard ducking: 40% during title cards, 8% during video
    vol = 0.08 if is_video else 0.40 
    
    bgm_segment = get_bgm(global_time, duration, vol)
    global_time += duration
    
    if bgm_segment is None: return clip
        
    if getattr(clip, 'audio', None) is not None and is_video:
        new_audio = CompositeAudioClip([clip.audio, bgm_segment])
        return clip.with_audio(new_audio)
    else:
        return clip.with_audio(bgm_segment)

def create_staggered_title_card(title, bullets, duration=5.0):
    def render_text(bullet_count):
        img = Image.new('RGBA', (1920, 1080), (15, 23, 42, 255))
        draw = ImageDraw.Draw(img)
        try:
            font_title = ImageFont.truetype("arialbd.ttf", 80)
            font_bullet = ImageFont.truetype("arial.ttf", 45)
        except:
            font_title = ImageFont.load_default()
            font_bullet = ImageFont.load_default()
            
        w_title = draw.textbbox((0,0), title, font=font_title)[2] if hasattr(draw, 'textbbox') else draw.textsize(title, font=font_title)[0]
        draw.text(((1920 - w_title)/2, 250), title, font=font_title, fill=(255, 255, 255, 255))
        draw.line([(1920/2 - 150, 370), (1920/2 + 150, 370)], fill=(234, 179, 8, 255), width=6)
        
        y_offset = 480
        for i in range(bullet_count):
            if i < len(bullets):
                b = bullets[i]
                w_b = draw.textbbox((0,0), b, font=font_bullet)[2] if hasattr(draw, 'textbbox') else draw.textsize(b, font=font_bullet)[0]
                draw.text(((1920 - w_b)/2, y_offset), b, font=font_bullet, fill=(200, 210, 224, 255))
                y_offset += 80
        return np.array(img)

    clips = []
    times = [0.8, 0.8, 0.8]
    rem = max(1.0, duration - sum(times))
    
    clips.append(ImageClip(render_text(0)).with_duration(times[0]))
    if len(bullets) > 0: clips.append(ImageClip(render_text(1)).with_duration(times[1]))
    if len(bullets) > 1: clips.append(ImageClip(render_text(2)).with_duration(times[2]))
    if len(bullets) > 2: clips.append(ImageClip(render_text(3)).with_duration(rem))
        
    try:
        final_card = concatenate_videoclips(clips)
    except:
        final_card = clips[-1] # fallback
    try:
        final_card = final_card.with_duration(duration)
    except:
        final_card = final_card.set_duration(duration)
    return add_bgm_to_clip(final_card, is_video=False)

def create_lower_third(text, duration):
    img = Image.new('RGBA', (1920, 1080), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img, 'RGBA')
    try:
        font = ImageFont.truetype("arialbd.ttf", 40)
    except:
        font = ImageFont.load_default()
        
    w = draw.textbbox((0,0), text, font=font)[2] if hasattr(draw, 'textbbox') else draw.textsize(text, font=font)[0]
    box_w, box_h = w + 80, 80
    x, y = 100, 900
    
    draw.rectangle([x, y, x + box_w, y + box_h], fill=(15, 23, 42, 220))
    draw.rectangle([x, y, x + 10, y + box_h], fill=(234, 179, 8, 255))
    draw.text((x + 40, y + 15), text, font=font, fill=(255, 255, 255, 255))
    
    return ImageClip(np.array(img)).with_duration(duration)

def prepare_professional_image(path, duration=5.0, zoom=True):
    if not os.path.exists(path): return None
    img = Image.open(path).convert('RGB')
    base_arr = np.array(img)
    bg_frame = cv2.resize(base_arr, (1920, 1080))
    bg_frame = cv2.GaussianBlur(bg_frame, (151, 151), 0)
    bg_frame = (bg_frame * 0.35).astype(np.uint8)
    
    def make_frame(t):
        frame = bg_frame.copy()
        z = (1.0 + 0.08 * (t / duration)) if zoom else 1.0
        new_h = int(1080 * z)
        new_w = int(base_arr.shape[1] * (new_h / base_arr.shape[0]))
        resized = cv2.resize(base_arr, (new_w, new_h))
        y1 = (new_h - 1080) // 2
        cropped = resized[y1:y1+1080, :, :]
        
        x_offset = (1920 - new_w) // 2
        if x_offset > 0: frame[:, x_offset:x_offset+new_w] = cropped
        else:
            x1 = (new_w - 1920) // 2
            frame[:, :] = cropped[:, x1:x1+1920]
        return frame
        
    clip = VideoClip(make_frame, duration=duration)
    return add_bgm_to_clip(clip, is_video=False)

def prepare_professional_clip(path, start=None, end=None, speed=1.0, blur_phone=False, remove_audio=False, feature_name=None, is_app_demo=False):
    if not os.path.exists(path): return None
    try:
        clip = VideoFileClip(path)
        if start is not None and end is not None: clip = clip.subclipped(start, end)
        elif start is not None: clip = clip.subclipped(start)
        elif end is not None: clip = clip.subclipped(0, end)
            
        if speed != 1.0:
            try: 
                from moviepy.video.fx import MultiplySpeed
                clip = clip.with_effects([MultiplySpeed(speed)])
            except: pass
            
        if remove_audio: clip = clip.without_audio()
            
        first_frame = clip.get_frame(0)
        bg_frame = cv2.resize(first_frame, (1920, 1080))
        bg_frame = cv2.GaussianBlur(bg_frame, (151, 151), 0)
        bg_frame = (bg_frame * 0.35).astype(np.uint8)
        bg = ImageClip(bg_frame).with_duration(clip.duration)
            
        if blur_phone:
            def redact(image):
                frame = image.copy()
                h, w, _ = frame.shape
                y1, y2 = int(h * 0.32), int(h * 0.37)
                x1, x2 = int(w * 0.15), int(w * 0.85)
                try:
                    roi = frame[y1:y2, x1:x2]
                    blur = cv2.GaussianBlur(roi, (81, 81), 0)
                    frame[y1:y2, x1:x2] = blur
                except:
                    frame[y1:y2, x1:x2] = (15, 23, 42)
                return frame
            if hasattr(clip, 'image_transform'): clip = clip.image_transform(redact)
            else: clip = clip.fl_image(redact)
            
        if is_app_demo:
            video_h = 900
            video_w = int(clip.w * (900 / clip.h))
            clip = clip.resized(height=900)
            
            frame_w, frame_h = video_w + 20, video_h + 20
            img = Image.new('RGBA', (frame_w, frame_h), (0, 0, 0, 0))
            draw = ImageDraw.Draw(img)
            try:
                draw.rounded_rectangle([0, 0, frame_w, frame_h], radius=35, fill=(0,0,0,0), outline=(200, 200, 200, 255), width=10)
                x1 = (frame_w - 120) // 2
                draw.rounded_rectangle([x1, 0, x1 + 120, 25], radius=10, fill=(0,0,0,255))
            except:
                draw.rectangle([0, 0, frame_w, frame_h], outline=(200, 200, 200, 255), width=10)
            
            frame_clip = ImageClip(np.array(img)).with_duration(clip.duration)
            layers = [bg, clip.with_position("center"), frame_clip.with_position("center")]
        else:
            clip = clip.resized(height=1080)
            layers = [bg, clip.with_position("center")]
            
        if feature_name:
            lt = create_lower_third(f"Feature: {feature_name}", clip.duration)
            layers.append(lt.with_position("center"))
            
        comp = CompositeVideoClip(layers)
        comp.audio = clip.audio
        return add_bgm_to_clip(comp, is_video=True)
    except Exception as e:
        print(f"Error processing {path}: {e}")
        return None

def add_progress_bar(clip):
    duration = clip.duration
    def draw_bar(get_frame, t):
        frame = get_frame(t).copy()
        progress = t / duration
        bar_w = int(1920 * progress)
        if bar_w > 0:
            frame[1080-8:1080, 0:bar_w] = (234, 179, 8)
        return frame
        
    try:
        return clip.transform(draw_bar)
    except AttributeError:
        return clip.fl(draw_bar)

def main():
    clips = []
    
    clips.append(create_staggered_title_card("Introducing AURA", [
        "• A revolutionary low-cost Edge-AI mobility system",
        "• Designed for the visually impaired",
        "• Enhancing independence and urban safety"
    ]))
    clips.append(prepare_professional_image(get_path("1scene2.jpeg"), duration=4.0, zoom=True))
    
    clips.append(create_staggered_title_card("Smart Hardware Integration", [
        "• Powered by the ESP32 Microcontroller",
        "• Ultrasonic sensors for forward obstacle detection",
        "• IR sensors for drop-offs and descending stairs",
        "• Water sensors to detect wet and slippery surfaces"
    ]))
    clips.append(prepare_professional_clip(get_path("scene3.mp4"), speed=1.5, remove_audio=True, feature_name="Hardware Array", is_app_demo=False))
    
    clips.append(create_staggered_title_card("AURA Android Application", [
        "• Seamless low-latency Bluetooth communication",
        "• Delivers continuous Text-to-Speech (TTS) guidance",
        "• 100% offline edge processing for maximum privacy"
    ]))
    clips.append(prepare_professional_clip(get_path("scene4.mp4"), feature_name="App Dashboard", is_app_demo=True))
    
    clips.append(create_staggered_title_card("Real-Time Hazard Detection", [
        "• Instantaneous auditory feedback",
        "• Detects distance and approaching obstacles",
        "• Eliminates the 'detection delay' of standard canes"
    ]))
    clips.append(prepare_professional_clip(get_path("scene5A1.mp4"), start=0, end=6, feature_name="Obstacle Alert", is_app_demo=True))
    clips.append(prepare_professional_clip(get_path("scene5A3.mp4"), start=3.6, end=13.6, feature_name="Obstacle Alert", is_app_demo=True))
    
    clips.append(create_staggered_title_card("AI Vision Camera System", [
        "• Smartphone-based intelligent recognition",
        "• Detects doors, stairs, and complex hazards",
        "• Extends environmental awareness beyond physical reach"
    ]))
    clips.append(prepare_professional_clip(get_path("scene5B2.mp4"), start=2.2, end=6.2, feature_name="AI Hazard Recognition", is_app_demo=True))
    clips.append(prepare_professional_clip(get_path("scene5B1.mp4"), start=5, end=8, feature_name="AI Hazard Recognition", is_app_demo=True))
    
    clips.append(create_staggered_title_card("OCR Text Reader", [
        "• Multilingual text scanning capabilities",
        "• Converts printed text into spoken audio",
        "• Empowers users to read signs and documents"
    ]))
    clips.append(prepare_professional_clip(get_path("scene5C.mp4"), start=3, end=14, feature_name="OCR Scanning", is_app_demo=True))
    clips.append(prepare_professional_clip(get_path("scene5C.mp4"), start=27, end=41, feature_name="OCR Scanning", is_app_demo=True))
    
    clips.append(create_staggered_title_card("Emergency Support System", [
        "• Single-tap emergency activation",
        "• Sends live GPS location via SMS",
        "• Automatically calls registered emergency contacts"
    ]))
    clips.append(prepare_professional_clip(get_path("scene5D1.mp4"), start=3.1, blur_phone=True, feature_name="Emergency SOS", is_app_demo=True))
    clips.append(prepare_professional_image(get_path("scene5D2.jpeg"), duration=3.0, zoom=False))
    
    clips.append(create_staggered_title_card("Empowering Independence", [
        "• Low-cost and highly scalable",
        "• No internet connection required",
        "• Built for everyone, everywhere."
    ]))
    clips.append(prepare_professional_image(get_path("scene6.jpeg"), duration=5.0, zoom=True))
    clips.append(create_staggered_title_card("AURA", [
        "Empowering independence through technology."
    ]))
    
    try:
        from moviepy.video.fx import FadeIn, FadeOut
        final_clips = [c.with_effects([FadeIn(0.4), FadeOut(0.4)]) for c in clips if c is not None]
    except Exception as e:
        print("Fade effects not applied:", e)
        final_clips = [c for c in clips if c is not None]

    final_clip = concatenate_videoclips(final_clips, method="compose")
    
    # Removed progress bar to speed up rendering

    final_clip.write_videofile(OUTPUT_FILE, fps=24, codec="libx264", audio_codec="aac", audio_fps=44100)
    print("Draft 9 Done!")

if __name__ == "__main__":
    main()
