import SwiftUI
import PhotosUI

public struct PhotoAuditView: View {
    @ObservedObject var firestore = FirestoreService.shared
    @Environment(\.dismiss) var dismiss
    @Environment(\.colorScheme) var systemScheme
    
    let task: RetailTask
    
    @State private var selectedPhotoItem: PhotosPickerItem? = nil
    @State private var capturedImage: UIImage? = nil
    @State private var isUploading = false
    @State private var fullscreenPhoto: TaskPhoto? = nil
    
    public var body: some View {
        let scheme = firestore.isDarkMode ? ColorScheme.dark : systemScheme
        
        NavigationStack {
            VStack(spacing: 14) {
                // Header
                VStack(alignment: .leading, spacing: 2) {
                    Text(task.title)
                        .font(.system(size: 15, weight: .black))
                    Text("AUDIT COMPLIANCE PHOTOS")
                        .font(.system(size: 10, weight: .heavy))
                        .foregroundColor(ShiftTheme.orange)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                
                // Photo Action
                HStack(spacing: 10) {
                    PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                        HStack(spacing: 6) {
                            Image(systemName: "camera.fill")
                            Text("TAKE / SELECT PHOTO")
                        }
                        .font(.system(size: 12, weight: .black))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 42)
                        .background(ShiftTheme.orange)
                        .cornerRadius(8)
                    }
                }
                
                // Photo Preview if selected
                if let image = capturedImage {
                    VStack(spacing: 8) {
                        Image(uiImage: image)
                            .resizable()
                            .scaledToFit()
                            .frame(maxHeight: 180)
                            .cornerRadius(8)
                        
                        Button {
                            uploadPhoto(image)
                        } label: {
                            HStack {
                                if isUploading {
                                    ProgressView()
                                        .tint(.white)
                                } else {
                                    Image(systemName: "icloud.and.arrow.up.fill")
                                    Text("CONFIRM & UPLOAD AUDIT PHOTO")
                                }
                            }
                            .font(.system(size: 12, weight: .black))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 40)
                            .background(ShiftTheme.greenDone)
                            .cornerRadius(8)
                        }
                        .disabled(isUploading)
                    }
                    .padding(10)
                    .background(ShiftTheme.surfaceSubtle(for: scheme))
                    .cornerRadius(8)
                }
                
                // Photos Grid
                let photos = firestore.taskPhotos.filter { $0.taskId == task.id }
                if photos.isEmpty {
                    VStack(spacing: 6) {
                        Image(systemName: "photo.on.rectangle.angled")
                            .font(.system(size: 30))
                            .foregroundColor(ShiftTheme.textSecondary(for: scheme))
                        Text("No audit photos uploaded today.")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(ShiftTheme.textSecondary(for: scheme))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(30)
                } else {
                    ScrollView {
                        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                            ForEach(photos) { photo in
                                Button {
                                    fullscreenPhoto = photo
                                } label: {
                                    VStack(alignment: .leading, spacing: 4) {
                                        if let imgData = Data(base64Encoded: photo.photoUri.replacingOccurrences(of: "data:image/jpeg;base64,", with: "")),
                                           let uiImg = UIImage(data: imgData) {
                                            Image(uiImage: uiImg)
                                                .resizable()
                                                .scaledToFill()
                                                .frame(height: 100)
                                                .clipped()
                                        } else {
                                            Rectangle()
                                                .fill(Color.gray.opacity(0.3))
                                                .frame(height: 100)
                                        }
                                        
                                        HStack {
                                            Text(photo.authorName)
                                                .font(.system(size: 9, weight: .bold))
                                                .foregroundColor(ShiftTheme.textPrimary(for: scheme))
                                            Spacer()
                                            Text(photo.dateString)
                                                .font(.system(size: 8))
                                                .foregroundColor(ShiftTheme.textSecondary(for: scheme))
                                        }
                                        .padding(.horizontal, 4)
                                        .padding(.bottom, 4)
                                    }
                                    .background(ShiftTheme.surface(for: scheme))
                                    .cornerRadius(6)
                                    .overlay(RoundedRectangle(cornerRadius: 6).stroke(ShiftTheme.border(for: scheme), lineWidth: 1))
                                }
                            }
                        }
                    }
                }
                
                Spacer()
            }
            .padding(16)
            .background(ShiftTheme.bg(for: scheme))
            .navigationTitle("Audit Photos")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .onChange(of: selectedPhotoItem) { newItem in
                Task {
                    if let data = try? await newItem?.loadTransferable(type: Data.self),
                       let img = UIImage(data: data) {
                        capturedImage = img
                    }
                }
            }
            .sheet(item: $fullscreenPhoto) { photo in
                fullscreenView(for: photo)
            }
        }
    }
    
    private func uploadPhoto(_ image: UIImage) {
        guard let compressedData = image.jpegData(compressionQuality: 0.6) else { return }
        let base64 = "data:image/jpeg;base64," + compressedData.base64EncodedString()
        isUploading = true
        
        Task {
            do {
                try await firestore.addPhoto(taskId: task.id, taskTitle: task.title, base64Data: base64)
                capturedImage = nil
                selectedPhotoItem = nil
                isUploading = false
            } catch {
                isUploading = false
            }
        }
    }
    
    private func fullscreenView(for photo: TaskPhoto) -> some View {
        ZStack {
            Color.black.ignoresSafeArea()
            VStack {
                HStack {
                    Spacer()
                    Button("Close") {
                        fullscreenPhoto = nil
                    }
                    .foregroundColor(.white)
                    .font(.system(size: 14, weight: .bold))
                }
                .padding()
                
                Spacer()
                
                if let imgData = Data(base64Encoded: photo.photoUri.replacingOccurrences(of: "data:image/jpeg;base64,", with: "")),
                   let uiImg = UIImage(data: imgData) {
                    Image(uiImage: uiImg)
                        .resizable()
                        .scaledToFit()
                }
                
                Spacer()
                
                Text("\(photo.authorName) • \(photo.dateString)")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(.gray)
                    .padding(.bottom, 20)
            }
        }
    }
}
