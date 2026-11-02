import React, { useState } from 'react';
import { BookOpen, CheckCircle, ExternalLink, ArrowRight, AlertTriangle } from 'lucide-react';

export default function Onboarding({ onComplete }) {
  const [step, setStep] = useState(0);

  const slides = [
    {
      title: "Welcome to ColorNest",
      icon: <BookOpen size={64} className="text-purple-600 mb-6 mx-auto" />,
      content: (
        <div className="space-y-4 text-gray-600 dark:text-gray-300">
          <p className="font-bold text-gray-800 dark:text-white text-lg">Important Disclaimer</p>
          <p>
            ColorNest is <strong>NOT</strong> a digital art app, a coloring app, or a photo storage app. 
          </p>
          <p>
            It is a beautiful, AI-powered catalog designed to help you organize the physical coloring books, printed pages, and line art you already own (whether they are saved in your phone's gallery or your cloud storage like Google Drive).
          </p>
          <p>
            Think of it as a library index for your hobby, not the drawing canvas itself.
          </p>
        </div>
      )
    },
    {
      title: "Organize & Finish",
      icon: <CheckCircle size={64} className="text-green-500 mb-6 mx-auto" />,
      content: (
        <div className="space-y-4 text-gray-600 dark:text-gray-300">
          <p className="font-bold text-gray-800 dark:text-white text-lg">Track Your Progress</p>
          <p>
            Upload photos of your pages, and use our AI to automatically sort them into deeply nested folders (like <em>Animals &gt; Birds &gt; Owls</em>).
          </p>
          <p>
            When you are done coloring a page, you can mark it as <strong>Completed</strong>. 
          </p>
          <p>
            This gives you a dedicated "Finished" section, allowing you to easily browse and show off all your finished artwork in one beautiful gallery at once.
          </p>
        </div>
      )
    },
    {
      title: "Where to find Line Art",
      icon: <ExternalLink size={64} className="text-blue-500 mb-6 mx-auto" />,
      content: (
        <div className="space-y-3 text-gray-600 dark:text-gray-300 text-sm">
          <p className="font-bold text-gray-800 dark:text-white text-lg mb-2">Free Resources</p>
          <p>Looking for new pages to color? Here are some great free sources for line art and coloring pages:</p>
          <ul className="space-y-2">
            <li><a href="https://www.supercoloring.com" target="_blank" rel="noreferrer" className="text-purple-600 font-bold underline">SuperColoring</a> - Massive library of thousands of pages.</li>
            <li><a href="https://www.crayola.com/free-coloring-pages" target="_blank" rel="noreferrer" className="text-purple-600 font-bold underline">Crayola</a> - Classic, reliable pages for all ages.</li>
            <li><a href="https://coloringpagespro.com" target="_blank" rel="noreferrer" className="text-purple-600 font-bold underline">Coloring Pages Pro</a> - High-quality, balanced compositions.</li>
            <li><a href="https://www.justcolor.net" target="_blank" rel="noreferrer" className="text-purple-600 font-bold underline">Just Color</a> - Artistic, adult-friendly, and Zentangle designs.</li>
            <li><a href="https://openclipart.org" target="_blank" rel="noreferrer" className="text-purple-600 font-bold underline">OpenClipart</a> - Public domain safe line art and vectors.</li>
          </ul>

          <div className="mt-4 p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-900 rounded-lg">
            <h4 className="font-bold text-red-600 dark:text-red-400 flex items-center gap-1 mb-1">
              <AlertTriangle size={14} /> Adult Content Warning
            </h4>
            <p className="text-xs text-gray-700 dark:text-gray-300 mb-2">
              <a href="https://www.deviantart.com/search?q=line+art" target="_blank" rel="noreferrer" className="text-purple-600 dark:text-purple-400 font-bold underline">DeviantArt</a> is a massive community for line art, but it is user-uploaded.
            </p>
            <p className="text-xs text-gray-700 dark:text-gray-300">
              Please be aware that search results may include NSFW (Not Safe For Work) or adult content. Use filters and browse responsibly.
            </p>
          </div>
        </div>
      )
    }
  ];

  const handleNext = () => {
    if (step < slides.length - 1) {
      setStep(step + 1);
    } else {
      onComplete();
    }
  };

  return (
    <div className="fixed inset-0 bg-black/80 flex justify-center items-center z-[999] p-4">
      <div className="bg-white dark:bg-gray-800 rounded-3xl w-full max-w-md p-6 shadow-2xl flex flex-col min-h-[400px]">
        
        {/* Header */}
        <div className="text-center mb-4">
          {slides[step].icon}
          <h2 className="text-2xl font-bold text-gray-800 dark:text-white">{slides[step].title}</h2>
        </div>

        {/* Content Area */}
        <div className="flex-grow overflow-y-auto mb-6">
          {slides[step].content}
        </div>

        {/* Footer / Navigation */}
        <div className="flex justify-between items-center mt-auto pt-4 border-t border-gray-100 dark:border-gray-700">
          {/* Progress Dots */}
          <div className="flex gap-1">
            {slides.map((_, i) => (
              <div key={i} className={`w-2 h-2 rounded-full ${i === step ? 'bg-purple-600' : 'bg-gray-300 dark:bg-gray-600'}`} />
            ))}
          </div>

          <button 
            onClick={handleNext}
            className="bg-purple-600 text-white px-6 py-2 rounded-xl font-bold flex items-center gap-2 hover:bg-purple-700 transition"
          >
            {step === slides.length - 1 ? "Let's Go!" : "Next"}
            {step !== slides.length - 1 && <ArrowRight size={16} />}
          </button>
        </div>

      </div>
    </div>
  );
}
